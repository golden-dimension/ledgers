package de.adorsys.ledgers.deposit.api.service.impl;

import de.adorsys.ledgers.deposit.db.domain.DepositAccount;
import de.adorsys.ledgers.deposit.db.domain.Payment;
import de.adorsys.ledgers.deposit.db.domain.PaymentTarget;
import de.adorsys.ledgers.deposit.db.repository.DepositAccountRepository;
import de.adorsys.ledgers.deposit.db.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExecutionScheduler {
    private static final String SCHEDULER = "Scheduler";
    private final PaymentRepository paymentRepository;
    private final DepositAccountRepository accountRepository;

    private final PaymentExecutionService executionService;

    @Scheduled(initialDelayString = "${paymentScheduler.initialDelay}", fixedDelayString = "${paymentScheduler.delay}")
    public void scheduler() {
        log.info("Payment Execution Scheduler started at {}", LocalDateTime.now());
        List<Payment> payments = paymentRepository.getAllDuePayments();
        payments.forEach(this::executeIfNotBlocked);
    }

    private void executeIfNotBlocked(Payment payment) {
        boolean debtorIsEnabled = isDebtorAccountEnabled(payment.getAccountId());
        boolean creditorsAreEnabled = areTargetsEnabled(payment.getTargets());

        if (debtorIsEnabled && creditorsAreEnabled) {
            executionService.executePayment(payment, SCHEDULER);
        }
    }

    private boolean isDebtorAccountEnabled(String accountId) {
        return accountRepository.findById(accountId)
                       .map(this::isDepositAccountEnabled)
                       .orElse(false);
    }

    private boolean areTargetsEnabled(List<PaymentTarget> targets) {
        return targets.stream()
                       .map(PaymentTarget::getCreditorAccount)
                       .map(ar -> accountRepository.findByIbanAndCurrency(ar.getIban(), ar.getCurrency())
                                          .orElse(null))
                       .anyMatch(this::isDepositAccountEnabled);
    }

    private boolean isDepositAccountEnabled(DepositAccount depositAccount) {
        return Optional.ofNullable(depositAccount)
                       .map(DepositAccount::isEnabled)
                       .orElse(false);
    }
}
