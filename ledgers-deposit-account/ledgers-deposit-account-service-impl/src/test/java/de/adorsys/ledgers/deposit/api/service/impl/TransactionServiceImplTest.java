package de.adorsys.ledgers.deposit.api.service.impl;

import de.adorsys.ledgers.deposit.api.domain.MockBookingDetailsBO;
import de.adorsys.ledgers.deposit.api.service.DepositAccountConfigService;
import de.adorsys.ledgers.deposit.api.service.mappers.SerializeService;
import de.adorsys.ledgers.postings.api.domain.ChartOfAccountBO;
import de.adorsys.ledgers.postings.api.domain.LedgerBO;
import de.adorsys.ledgers.postings.api.service.LedgerService;
import de.adorsys.ledgers.postings.api.service.PostingMockService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Currency;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TransactionServiceImplTest {
    private static final String USER_ACCOUNT = "userAccount";
    private static final String REMITTANCE = "remittance";
    private static final String CR_DR_NAME = "crDrName";
    private static final String OTHER_ACCOUNT = "otherAccount";
    private static final Currency CURRENCY = Currency.getInstance("EUR");

    @InjectMocks
    private TransactionServiceImpl transactionService;
    @Mock
    private SerializeService serializeService;
    @Mock
    private PostingMockService postingService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private DepositAccountConfigService depositAccountConfigService;

    @Test
    public void bookMockTransaction_depositPosting() {
        //given
        when(depositAccountConfigService.getLedger()).thenReturn("ledger");
        when(ledgerService.findLedgerByName(any())).thenReturn(Optional.of(getLedger()));

        //when
        Map<String, String> map = transactionService.bookMockTransaction(Collections.singletonList(getMockBookingDetailsBO(BigDecimal.TEN)));

        //then
        assertTrue(map.isEmpty());
        verify(depositAccountConfigService, times(1)).getLedger();
        verify(ledgerService, times(1)).findLedgerByName("ledger");
    }

    @Test
    public void bookMockTransaction_paymentPosting() {
        //given
        when(depositAccountConfigService.getLedger()).thenReturn("ledger");
        when(ledgerService.findLedgerByName(any())).thenReturn(Optional.of(getLedger()));

        //when
        Map<String, String> map = transactionService.bookMockTransaction(Collections.singletonList(getMockBookingDetailsBO(BigDecimal.valueOf(-1))));

        //then
        assertTrue(map.isEmpty());
        verify(depositAccountConfigService, times(1)).getLedger();
        verify(ledgerService, times(1)).findLedgerByName("ledger");
    }

    @Test(expected = IllegalStateException.class)
    public void bookMockTransaction_ledgersNotFound() {
        //given
        when(depositAccountConfigService.getLedger()).thenReturn("ledger");

        //when
        transactionService.bookMockTransaction(Collections.singletonList(getMockBookingDetailsBO(BigDecimal.TEN)));
    }

    private MockBookingDetailsBO getMockBookingDetailsBO(BigDecimal amount) {
        MockBookingDetailsBO details = new MockBookingDetailsBO();
        details.setUserAccount(USER_ACCOUNT);
        details.setBookingDate(LocalDate.now());
        details.setValueDate(LocalDate.now());
        details.setRemittance(REMITTANCE);
        details.setCrDrName(CR_DR_NAME);
        details.setOtherAccount(OTHER_ACCOUNT);
        details.setAmount(amount);
        details.setCurrency(CURRENCY);
        return details;
    }

    private LedgerBO getLedger() {
        LedgerBO ledgerBO = new LedgerBO();
        ledgerBO.setName("ledger");
        ledgerBO.setCoa(new ChartOfAccountBO("name", "id", LocalDateTime.now(), "userDetails", "shortDesc", "longDesc"));
        return ledgerBO;
    }
}