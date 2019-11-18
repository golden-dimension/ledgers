package de.adorsys.ledgers.middleware.impl.converter;

import de.adorsys.ledgers.deposit.api.domain.*;
import de.adorsys.ledgers.jaxb.api.in.*;
import de.adorsys.ledgers.jaxb.api.out.CustomerPaymentStatusReportV03;
import de.adorsys.ledgers.jaxb.api.out.OriginalGroupInformation20;
import de.adorsys.ledgers.jaxb.api.out.OriginalPaymentInformation1;
import de.adorsys.ledgers.jaxb.api.out.TransactionGroupStatus3Code;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.SCAPaymentResponseTO;
import de.adorsys.ledgers.middleware.api.exception.MiddlewareModuleException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import static de.adorsys.ledgers.middleware.api.exception.MiddlewareErrorCode.PAYMENT_PROCESSING_FAILURE;

@Component
@RequiredArgsConstructor
public class PainPaymentConverterImpl implements PainPaymentConverter {
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("EUR");

    private final JaxbConverter jaxbConverter;

    @Override
    public String toPayload(SCAPaymentResponseTO response) {
        de.adorsys.ledgers.jaxb.api.out.Document payload = new de.adorsys.ledgers.jaxb.api.out.Document();
        CustomerPaymentStatusReportV03 status = new CustomerPaymentStatusReportV03();
        OriginalGroupInformation20 original = new OriginalGroupInformation20();
        TransactionGroupStatus3Code transactionStatus = TransactionGroupStatus3Code.valueOf(response.getTransactionStatus().toString());
        original.setGrpSts(transactionStatus);
        status.setOrgnlGrpInfAndSts(original);
        OriginalPaymentInformation1 paymentInfo = new OriginalPaymentInformation1();
        paymentInfo.setPmtInfSts(transactionStatus);
        paymentInfo.setOrgnlPmtInfId(response.getPaymentId());
        status.getOrgnlPmtInfAndSts().add(paymentInfo);
        payload.setCstmrPmtStsRpt(status);
        return jaxbConverter.fromObject(payload)
                       .orElseThrow(() -> MiddlewareModuleException.builder()
                                                  .devMsg("Couldn't convert object to payment object.")
                                                  .errorCode(PAYMENT_PROCESSING_FAILURE)
                                                  .build());
    }

    @Override
    public PaymentBO toPaymentBO(String payment, PaymentTypeTO paymentType) {
        Document document = jaxbConverter.toObject(payment, Document.class)
                                    .orElseThrow(() -> MiddlewareModuleException.builder()
                                                               .devMsg("Couldn't convert xml to payment object.")
                                                               .errorCode(PAYMENT_PROCESSING_FAILURE)
                                                               .build());
        PaymentBO paymentBO = new PaymentBO();
        paymentBO.setPaymentType(PaymentTypeBO.valueOf(paymentType.name()));
        paymentBO.setTargets(buildPaymentsTarget(document));
        paymentBO.setDebtorAccount(buildAccountReferenceBO(getDebtorAccount(document)));
        paymentBO.setRequestedExecutionDate(buildRequestedExecutionDate(document));
        return paymentBO;
    }

    private LocalDate buildRequestedExecutionDate(Document document) {
        XMLGregorianCalendar reqdExctnDt = document.getCstmrCdtTrfInitn()
                                                   .getPmtInves()
                                                   .get(0).getReqdExctnDt();
        return LocalDate.of(reqdExctnDt.getYear(), reqdExctnDt.getMonth(), reqdExctnDt.getDay());
    }

    private List<PaymentTargetBO> buildPaymentsTarget(Document painPayment) {
        return painPayment.getCstmrCdtTrfInitn()
                       .getPmtInves().stream()
                       .map(this::buildPaymentTargetBO)
                       .collect(Collectors.toList());
    }

    private CashAccount16 getDebtorAccount(Document painPayment) {
        PaymentInstructionInformation3 paymentInstructionInformation3 = painPayment.getCstmrCdtTrfInitn().getPmtInves().get(0);
        return paymentInstructionInformation3.getDbtrAcct();
    }

    private PaymentTargetBO buildPaymentTargetBO(PaymentInstructionInformation3 paymentInfo) {
        ServiceLevel8Choice serviceChoice = paymentInfo.getPmtTpInf().getSvcLvl();
        CreditTransferTransactionInformation10 creditTransferInfo = getCreditTransferTransactionInformation(paymentInfo);
        PaymentIdentification1 pmtId = creditTransferInfo.getPmtId();

        PaymentTargetBO target = new PaymentTargetBO();
        target.setCreditorName(creditTransferInfo.getCdtr().getNm());
        target.setPaymentProduct(PaymentProductBO.valueOf(serviceChoice.getCd()));
        target.setEndToEndIdentification(pmtId.getEndToEndId());
        target.setCreditorName(creditTransferInfo.getCdtr().getNm());
        target.setCreditorAccount(buildAccountReferenceBO(creditTransferInfo.getCdtrAcct()));
        target.setRemittanceInformationUnstructured(creditTransferInfo.getRmtInf().getUstrds().get(0));
        target.setInstructedAmount(buildAmountBO(creditTransferInfo.getAmt()));
        return target;
    }

    private AmountBO buildAmountBO(AmountType3Choice amt) {
        ActiveOrHistoricCurrencyAndAmount instdAmt = amt.getInstdAmt();
        return new AmountBO(Currency.getInstance(instdAmt.getCcy()), instdAmt.getValue());
    }

    private AccountReferenceBO buildAccountReferenceBO(CashAccount16 cashAccount) {
        AccountReferenceBO account = new AccountReferenceBO();
        account.setIban(cashAccount.getId().getIBAN());
        Currency currency = StringUtils.isNoneBlank(cashAccount.getCcy())
                                    ? Currency.getInstance(cashAccount.getCcy())
                                    : DEFAULT_CURRENCY;
        account.setCurrency(currency);
        return account;
    }

    private CreditTransferTransactionInformation10 getCreditTransferTransactionInformation(PaymentInstructionInformation3 paymentInfo) {
        return paymentInfo.getCdtTrfTxInves().get(0);
    }
}
