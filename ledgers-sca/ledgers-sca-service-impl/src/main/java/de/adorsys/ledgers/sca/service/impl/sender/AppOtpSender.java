package de.adorsys.ledgers.sca.service.impl.sender;

import de.adorsys.ledgers.sca.domain.sca.message.AppScaMessage;
import de.adorsys.ledgers.sca.domain.sca.message.ScaMessage;
import de.adorsys.ledgers.sca.service.SCASender;
import de.adorsys.ledgers.um.api.domain.ScaMethodTypeBO;
import de.adorsys.ledgers.util.exception.ScaModuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static de.adorsys.ledgers.um.api.domain.ScaMethodTypeBO.APP_OTP;

@Service
@RequiredArgsConstructor
public class AppOtpSender implements SCASender {
    private final RestTemplate template;

    @Override
    public <T extends ScaMessage> boolean send(T message) {
        AppScaMessage scaMessage = (AppScaMessage) message;
        HttpMethod method = Optional.ofNullable(HttpMethod.resolve(scaMessage.getSocketServiceHttpMethod()))
                                    .orElseThrow(() -> ScaModuleException.buildScaSenderException("Could not parse SocketServiceHttpMethod"));
        HttpEntity<AppScaMessage> httpEntity = new HttpEntity<>(scaMessage);
        ResponseEntity<Void> exchange = template.exchange(scaMessage.getSocketServicePath(), method, httpEntity, Void.class);
        return exchange.getStatusCode().is2xxSuccessful();
    }

    @Override
    public ScaMethodTypeBO getType() {
        return APP_OTP;
    }
}
