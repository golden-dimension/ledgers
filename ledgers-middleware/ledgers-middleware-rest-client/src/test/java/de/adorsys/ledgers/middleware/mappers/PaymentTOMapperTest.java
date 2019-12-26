package de.adorsys.ledgers.middleware.mappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import de.adorsys.ledgers.middleware.api.domain.account.AccountReferenceTO;
import de.adorsys.ledgers.middleware.api.domain.general.AddressTO;
import de.adorsys.ledgers.middleware.api.domain.payment.AmountTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTargetTO;
import de.adorsys.ledgers.middleware.client.mappers.PaymentMapperConfiguration;
import de.adorsys.ledgers.middleware.client.mappers.PaymentMapperTO;
import org.junit.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO.BULK;
import static de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO.SINGLE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class PaymentTOMapperTest {
    private static final ObjectMapper STATIC_MAPPER = new ObjectMapper()
                                                              .findAndRegisterModules()
                                                              .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                                                              .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                                                              .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false)
                                                              .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                                                              .registerModule(new Jdk8Module())
                                                              .registerModule(new JavaTimeModule())
                                                              .registerModule(new ParameterNamesModule());
    private static final PaymentMapperConfiguration configuration = new PaymentMapperConfiguration(STATIC_MAPPER);

    @Test
    public void xs2aPaymentJson() throws IOException {
        PaymentMapperTO mapper = configuration.paymentMapperTO();
        String payment = readPayment("xs2aSinglePayment.json");
        PaymentTO result = mapper.toAbstractPayment(payment, "SINGLE", "sepa-credit-transfers");

        assertThat(result).isEqualToComparingFieldByFieldRecursively(getSinglePmt());
    }

    @Test
    public void xs2aOldFormatAddress() throws IOException {
        PaymentMapperTO mapper = configuration.paymentMapperTO();
        String payment = readPayment("xs2aSinglePaymentOld.json");
        PaymentTO result = mapper.toAbstractPayment(payment, "SINGLE", "sepa-credit-transfers");

        assertThat(result).isEqualToComparingFieldByFieldRecursively(getSinglePmt());
    }

    @Test
    public void xs2aBulk() throws IOException {
        PaymentMapperTO mapper = configuration.paymentMapperTO();
        String payment = readPayment("xs2aBulkPaymentOld.json");
        PaymentTO result = mapper.toAbstractPayment(payment, "BULK", "sepa-credit-transfers");

        assertThat(result).isEqualToComparingFieldByFieldRecursively(getBulk());
    }

    private PaymentTO getSinglePmt() {
        PaymentTO payment = new PaymentTO();
        payment.setPaymentType(SINGLE);
        payment.setPaymentProduct("sepa-credit-transfers");
        payment.setDebtorAccount(new AccountReferenceTO("DE40500105178578796457", null, null, null, null, Currency.getInstance("USD")));
        payment.setTargets(getTargets());
        return payment;
    }

    private List<PaymentTargetTO> getTargets() {
        PaymentTargetTO target = new PaymentTargetTO();
        target.setEndToEndIdentification("WBG-123456789");
        target.setInstructedAmount(new AmountTO(Currency.getInstance("CHF"), new BigDecimal("1.00")));
        target.setCreditorAccount(new AccountReferenceTO("DE40500105178578796457", null, null, null, null, Currency.getInstance("EUR")));
        target.setCreditorAgent("AAAADEBBXXX");
        target.setCreditorAddress(new AddressTO("WBG Straße", "56", "Nürnberg", "90543", "DE", null, null));
        target.setCreditorName("WBG");
        target.setRemittanceInformationUnstructured("Ref. Number WBG-1222");
        ArrayList<PaymentTargetTO> targets = new ArrayList<>();
        targets.add(target);
        return targets;
    }

    private String readPayment(String file) {
        ResourceLoader loader = new DefaultResourceLoader();
        Resource resource = loader.getResource(file);
        try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PaymentTO getBulk() {
        PaymentTO payment = getSinglePmt();
        payment.setPaymentType(BULK);
        PaymentTargetTO target = new PaymentTargetTO();
        target.setEndToEndIdentification("RI-234567890");
        target.setInstructedAmount(new AmountTO(Currency.getInstance("EUR"), new BigDecimal("71.07")));
        target.setCreditorAccount(new AccountReferenceTO("DE03500105172351985719", null, null, null, null, Currency.getInstance("EUR")));
        target.setCreditorAgent("AAAADEBBXXX");
        target.setCreditorAddress(new AddressTO("Kaisergasse", "74", "Dresden", "01067", "DE", null, null));
        target.setCreditorName("Grünstrom");
        target.setRemittanceInformationUnstructured("Ref. Number GRUENSTROM-2444");
        payment.getTargets().add(target);
        return payment;
    }
}
