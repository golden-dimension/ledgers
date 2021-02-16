package de.adorsys.ledgers.middleware.impl.service;

import de.adorsys.ledgers.deposit.api.domain.PaymentBO;
import de.adorsys.ledgers.deposit.api.domain.TransactionStatusBO;
import de.adorsys.ledgers.deposit.api.service.DepositAccountPaymentService;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.TransactionStatusTO;
import de.adorsys.ledgers.middleware.api.domain.sca.GlobalScaResponseTO;
import de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaInfoTO;
import de.adorsys.ledgers.middleware.api.domain.sca.ScaStatusTO;
import de.adorsys.ledgers.middleware.api.domain.um.AisConsentTO;
import de.adorsys.ledgers.middleware.api.domain.um.BearerTokenTO;
import de.adorsys.ledgers.middleware.api.exception.MiddlewareErrorCode;
import de.adorsys.ledgers.middleware.api.exception.MiddlewareModuleException;
import de.adorsys.ledgers.middleware.api.service.OperationService;
import de.adorsys.ledgers.middleware.impl.converter.AisConsentBOMapper;
import de.adorsys.ledgers.middleware.impl.converter.PaymentConverter;
import de.adorsys.ledgers.middleware.impl.converter.UserMapper;
import de.adorsys.ledgers.middleware.impl.policies.PaymentCancelPolicy;
import de.adorsys.ledgers.sca.domain.OpTypeBO;
import de.adorsys.ledgers.sca.service.SCAOperationService;
import de.adorsys.ledgers.um.api.domain.AisConsentBO;
import de.adorsys.ledgers.um.api.domain.UserBO;
import de.adorsys.ledgers.um.api.service.UserService;
import de.adorsys.ledgers.util.Ids;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

import static de.adorsys.ledgers.deposit.api.domain.TransactionStatusBO.*;
import static de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO.CONSENT;
import static de.adorsys.ledgers.middleware.api.domain.sca.OpTypeTO.PAYMENT;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OperationServiceImpl implements OperationService { //TODO Requires a new messageResolver https://git.adorsys.de/adorsys/xs2a/psd2-dynamic-sandbox/-/issues/893
    private final DepositAccountPaymentService paymentService;
    private final PaymentConverter paymentConverter;
    private final SCAUtils scaUtils;
    private final AccessService accessService;
    private final UserMapper userMapper;
    private final UserService userService;
    private final AisConsentBOMapper aisConsentMapper;
    private final PaymentSupportService supportService;
    private final SCAOperationService scaOperationService;

    @Value("${ledgers.sca.multilevel.enabled:false}")
    private boolean multilevelScaEnable;

    @Override
    public <T> GlobalScaResponseTO resolveInitiation(OpTypeTO opType, String opId, T object, ScaInfoTO scaInfo) {
        UserBO user = scaUtils.userBO(scaInfo.getUserLogin());
        GlobalScaResponseTO response = resolveBasicResponse(false, opType, user, scaInfo);
        int scaWeight;
        if (CONSENT == opType) {
            AisConsentBO consent = aisConsentMapper.toAisConsentBO((AisConsentTO) object);
            scaWeight = user.resolveMinimalWeightForIbanSet(consent.getUniqueIbans());
            consent = userService.storeConsent(consent);
            response.setOperationObjectId(consent.getId());
        } else {
            scaWeight = resolveForPmtType(opType, opId, (PaymentTO) object, user, response);
        }
        response.setMultilevelScaRequired(multilevelScaEnable && scaWeight < 100);
        return response;
    }

    @Override
    public GlobalScaResponseTO execute(OpTypeTO opType, String opId, ScaInfoTO scaInfo) {
        if (opType == CONSENT) {
            throw MiddlewareModuleException.builder()
                          .devMsg("Consent execution not supported")
                          .errorCode(MiddlewareErrorCode.UNSUPPORTED_OPERATION)
                          .build();
        }
        PaymentBO payment = paymentService.getPaymentById(opId);
        OpTypeBO opTypeBO = OpTypeBO.valueOf(opType.name());
        boolean authenticationCompleted = scaOperationService.authenticationCompleted(opId, opTypeBO);
        UserBO user = scaUtils.userBO(scaInfo.getUserLogin());
        GlobalScaResponseTO response = resolveBasicResponse(true, opType, user, scaInfo);
        if (authenticationCompleted) {
            performExecuteOrCancelOperation(scaInfo, opId, opTypeBO, payment);
        } else if (multilevelScaEnable) {
            payment.setTransactionStatus(paymentService.updatePaymentStatus(opId, PATC));
            response.setMultilevelScaRequired(true);
            response.setPartiallyAuthorised(true);
        }
        response.setOperationObjectId(payment.getPaymentId());
        response.setTransactionStatus(TransactionStatusTO.valueOf(payment.getTransactionStatus().name()));
        return response;
    }

    private void performExecuteOrCancelOperation(ScaInfoTO scaInfoTO, String paymentId, OpTypeBO opType, PaymentBO payment) {
        if (opType == OpTypeBO.PAYMENT) {
            paymentService.updatePaymentStatus(paymentId, ACTC);
            payment.setTransactionStatus(paymentService.executePayment(paymentId, scaInfoTO.getUserLogin()));
        } else {
            payment.setTransactionStatus(paymentService.cancelPayment(paymentId));
        }
    }

    private int resolveForPmtType(OpTypeTO opType, String opId, PaymentTO object, UserBO user, GlobalScaResponseTO response) {
        PaymentBO payment;
        if (PAYMENT == opType) {
            payment = paymentConverter.toPaymentBO(object);
            supportService.validatePayment(payment);
            payment.updateDebtorAccountCurrency(supportService.getCheckedAccount(payment).getCurrency());
            TransactionStatusBO status = user.hasSCA() ? ACCP : ACTC;
            payment = persist(payment, status);
        } else {
            payment = paymentService.getPaymentById(opId);
            payment.setRequestedExecutionTime(LocalTime.now().plusMinutes(10));
            PaymentCancelPolicy.onCancel(opId, payment.getTransactionStatus());
        }
        response.setOperationObjectId(payment.getPaymentId());
        response.setTransactionStatus(TransactionStatusTO.valueOf(payment.getTransactionStatus().name()));
        return user.resolveWeightForAccount(payment.getAccountId());
    }

    @SuppressWarnings("java:S3358")
    private GlobalScaResponseTO resolveBasicResponse(boolean isFinal, OpTypeTO opType, UserBO user, ScaInfoTO scaInfo) {
        boolean isScaRequired = !isFinal && user.hasSCA();
        String psuMessage = null; //TODO new messageResolver
        BearerTokenTO token = isFinal ? null
                                      : accessService.exchangeTokenStartSca(isScaRequired, scaInfo.getAccessToken());
        ScaStatusTO scaStatus = isFinal ? ScaStatusTO.FINALISED
                                        : isScaRequired ? ScaStatusTO.PSUAUTHENTICATED : ScaStatusTO.EXEMPTED;

        return new GlobalScaResponseTO(opType, token, scaStatus, userMapper.toScaUserDataListTO(user.getScaUserData()), psuMessage);
    }

    private PaymentBO persist(PaymentBO paymentBO, TransactionStatusBO status) {
        if (paymentBO.getPaymentId() == null) {
            paymentBO.setPaymentId(Ids.id());
        }
        return paymentService.initiatePayment(paymentBO, status);
    }
}
