package de.adorsys.ledgers.middleware.impl.service;

import de.adorsys.ledgers.um.api.domain.EmailVerificationBO;
import de.adorsys.ledgers.um.api.domain.EmailVerificationStatusBO;
import de.adorsys.ledgers.um.api.domain.ScaUserDataBO;
import de.adorsys.ledgers.um.api.service.ScaUserDataService;
import de.adorsys.ledgers.um.api.service.ScaVerificationService;
import de.adorsys.ledgers.util.exception.UserManagementModuleException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import pro.javatar.commons.reader.ResourceReader;
import pro.javatar.commons.reader.YamlReader;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EmailVerificationServiceImplTest {

    @InjectMocks
    private EmailVerificationServiceImpl emailVerificationService;
    @Mock
    private ScaVerificationService scaVerificationService;
    @Mock
    private ScaUserDataService scaUserDataService;

    private ResourceReader reader = YamlReader.getInstance();

    private static final String EMAIL = "google@gmail.com";
    private static final String VERIFICATION_TOKEN = "Fz-4Kb6vREgj38CpsUAtSI";
    private static final LocalDateTime date = LocalDateTime.now();
    private static final EmailVerificationStatusBO STATUS_PENDING = EmailVerificationStatusBO.PENDING;
    private ScaUserDataBO scaUserDataBO;

    @Before
    public void setUp() {
        scaUserDataBO = readScaUserDataBO();
    }

    @Test
    public void createVerificationToken() {
        when(scaUserDataService.findByEmail(any())).thenReturn(scaUserDataBO);
        when(scaVerificationService.findByScaIdAndStatusNot(any(), any())).thenReturn(getEmailVerificationBO(date));

        String token = emailVerificationService.createVerificationToken(EMAIL);
        assertFalse(token.isEmpty());
    }

    @Test
    public void sendVerificationEmail() {
        when(scaVerificationService.findByToken(any())).thenReturn(getEmailVerificationBO(date));
        when(scaVerificationService.sendMessage(any(), any(), any(), any())).thenReturn(true);
        when(emailVerificationService.formatMessage("", "", "", "", LocalDateTime.now(), "")).thenReturn(anyString());

        emailVerificationService.sendVerificationEmail(VERIFICATION_TOKEN);
    }

    @Test
    public void confirmUser() {
        when(scaVerificationService.findByTokenAndStatus(any(), any())).thenReturn(getEmailVerificationBO(date.plusWeeks(1)));
        when(scaUserDataService.findByEmail(any())).thenReturn(scaUserDataBO);

        emailVerificationService.confirmUser(VERIFICATION_TOKEN);
    }

    @Test(expected = UserManagementModuleException.class)
    public void confirmUser_expiredToken() {
        when(scaVerificationService.findByTokenAndStatus(any(), any())).thenReturn(getEmailVerificationBO(date.minusWeeks(1)));

        emailVerificationService.confirmUser(VERIFICATION_TOKEN);
    }

    private ScaUserDataBO readScaUserDataBO() {
        try {
            return reader.getObjectFromResource(getClass(), "sca-user-data.yml", ScaUserDataBO.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private EmailVerificationBO getEmailVerificationBO(LocalDateTime date) {
        EmailVerificationBO bo = new EmailVerificationBO();
        bo.setToken(VERIFICATION_TOKEN);
        bo.setStatus(STATUS_PENDING);
        bo.setIssuedDateTime(LocalDateTime.now());
        bo.setExpiredDateTime(date);
        bo.setScaUserData(scaUserDataBO);
        return bo;
    }
}