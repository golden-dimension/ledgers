package de.adorsys.ledgers.app.ledgersInitiation;

import de.adorsys.ledgers.deposit.api.domain.DepositAccountBO;
import de.adorsys.ledgers.deposit.api.domain.DepositAccountDetailsBO;
import de.adorsys.ledgers.deposit.api.domain.TransactionDetailsBO;
import de.adorsys.ledgers.deposit.api.exception.DepositAccountNotFoundException;
import de.adorsys.ledgers.deposit.api.service.DepositAccountInitService;
import de.adorsys.ledgers.deposit.api.service.DepositAccountService;
import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.payment.BulkPaymentTO;
import de.adorsys.ledgers.middleware.api.domain.payment.PaymentTypeTO;
import de.adorsys.ledgers.middleware.api.domain.payment.SinglePaymentTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccountAccessTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserRoleTO;
import de.adorsys.ledgers.middleware.api.domain.um.UserTO;
import de.adorsys.ledgers.middleware.api.service.MiddlewarePaymentService;
import de.adorsys.ledgers.middleware.impl.converter.AccountDetailsMapper;
import de.adorsys.ledgers.middleware.impl.converter.UserMapper;
import de.adorsys.ledgers.mockbank.simple.data.BulkPaymentsData;
import de.adorsys.ledgers.mockbank.simple.data.MockbankInitData;
import de.adorsys.ledgers.mockbank.simple.data.SinglePaymentsData;
import de.adorsys.ledgers.um.api.exception.UserAlreadyExistsException;
import de.adorsys.ledgers.um.api.exception.UserNotFoundException;
import de.adorsys.ledgers.um.api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class BankInitService {
    private final MockbankInitData mockbankInitData;
    private final UserService userService;
    private final Environment env;
    private final UserMapper userMapper;
    private final Logger logger = LoggerFactory.getLogger(BankInitService.class);
    private final DepositAccountInitService depositAccountInitService;
    private final DepositAccountService depositAccountService;
    private final AccountDetailsMapper accountDetailsMapper;
    private final PaymentRestInitiationService restInitiationService;

    private static final String ACCOUNT_NOT_FOUND_MSG = "Account not Found! Should never happen while initiating mock data!";
    private static final String NO_USER_BY_IBAN = "Could not get User By Iban {}";

    @Autowired
    public BankInitService(MockbankInitData mockbankInitData, UserService userService, Environment env, UserMapper userMapper,
                           DepositAccountInitService depositAccountInitService, DepositAccountService depositAccountService,
                           AccountDetailsMapper accountDetailsMapper, MiddlewarePaymentService paymentService, PaymentRestInitiationService restInitiationService) {
        this.mockbankInitData = mockbankInitData;
        this.userService = userService;
        this.env = env;
        this.userMapper = userMapper;
        this.depositAccountInitService = depositAccountInitService;
        this.depositAccountService = depositAccountService;
        this.accountDetailsMapper = accountDetailsMapper;
        this.restInitiationService = restInitiationService;
    }

    public void init() {
        depositAccountInitService.initConfigData();
        if (Arrays.asList(this.env.getActiveProfiles()).contains("develop")) {
            uploadTestData();
        }
    }

    private void uploadTestData() {
        createUsers();
        createAccounts();
        //performTransactions(); //TODO Currently dupes bulk payments. Fix next MR. Add Separate Profiles to include/exclude payments
    }

    private void performTransactions() {
        List<UserTO> users = mockbankInitData.getUsers();
        performSinglePayments(users);
        performBulkPayments(users);
    }

    private void performSinglePayments(List<UserTO> users) {
        for (SinglePaymentsData paymentsData : mockbankInitData.getSinglePayments()) {
            SinglePaymentTO payment = paymentsData.getSinglePayment();
            try {
                if (transactionIsAbsent(payment.getDebtorAccount().getIban(), payment.getEndToEndIdentification())) {
                    UserTO user = getUserByIban(users, payment.getDebtorAccount().getIban());
                    restInitiationService.executePayment(user, PaymentTypeTO.SINGLE, payment);
                }
            } catch (DepositAccountNotFoundException e) {
                logger.error(ACCOUNT_NOT_FOUND_MSG);
            } catch (UserNotFoundException e) {
                logger.error(NO_USER_BY_IBAN, payment.getDebtorAccount().getIban());
            }
        }
    }

    private void performBulkPayments(List<UserTO> users) {
        for (BulkPaymentsData paymentsData : mockbankInitData.getBulkPayments()) {
            BulkPaymentTO payment = paymentsData.getBulkPayment();
            try {
                if (transactionIsAbsent(payment.getDebtorAccount().getIban(), payment.getPayments().get(0).getEndToEndIdentification())) {
                    UserTO user = getUserByIban(users, payment.getDebtorAccount().getIban());
                    restInitiationService.executePayment(user, PaymentTypeTO.BULK, payment);
                }
            } catch (DepositAccountNotFoundException e) {
                logger.error(ACCOUNT_NOT_FOUND_MSG);
            } catch (UserNotFoundException e) {
                logger.error(NO_USER_BY_IBAN, payment.getDebtorAccount().getIban());
            }
        }
    }

    private UserTO getUserByIban(List<UserTO> users, String iban) throws UserNotFoundException {
        return users.stream()
                       .filter(user -> ibanIsInAccess(iban, user))
                       .findFirst()
                       .orElseThrow(UserNotFoundException::new);
    }

    private boolean ibanIsInAccess(String iban, UserTO user) {
        return user.getAccountAccesses().stream()
                       .anyMatch(access -> access.getIban().equals(iban));
    }

    private boolean transactionIsAbsent(String iban, String entToEndId) throws DepositAccountNotFoundException {
        DepositAccountDetailsBO account = depositAccountService.getDepositAccountByIban(iban, LocalDateTime.now(), false);
        List<TransactionDetailsBO> transactions = depositAccountService.getTransactionsByDates(account.getAccount().getId(), LocalDateTime.of(2018, 1, 1, 1, 1), LocalDateTime.now());
        return transactions.stream()
                       .noneMatch(t -> entToEndId.equals(t.getEndToEndId()));
    }

    private void createAccounts() {
        for (AccountDetailsTO details : mockbankInitData.getAccounts()) {
            try {
                depositAccountService.getDepositAccountByIban(details.getIban(), LocalDateTime.now(), false);
            } catch (DepositAccountNotFoundException e) {
                createAccount(details);
            }
        }
    }

    private void createAccount(AccountDetailsTO details) {
        try {
            String userName = getUserNameByIban(details.getIban());
            DepositAccountBO accountBO = accountDetailsMapper.toDepositAccountBO(details);
            depositAccountService.createDepositAccount(accountBO, userName);
        } catch (UserNotFoundException | DepositAccountNotFoundException e) {
            logger.error("Error creating Account For Mocked User");
        }
    }

    private String getUserNameByIban(String iban) throws UserNotFoundException {
        return mockbankInitData.getUsers().stream()
                       .filter(u -> isAccountContainedInAccess(u.getAccountAccesses(), iban))
                       .findFirst()
                       .map(UserTO::getLogin)
                       .orElseThrow(UserNotFoundException::new);
    }

    private boolean isAccountContainedInAccess(List<AccountAccessTO> access, String iban) {
        return access.stream()
                       .anyMatch(a -> a.getIban().equals(iban));
    }

    private void createUsers() {
        for (UserTO user : mockbankInitData.getUsers()) {
            try {
                userService.findByLogin(user.getLogin());
            } catch (UserNotFoundException e) {
                user.getUserRoles().add(UserRoleTO.CUSTOMER);
                createUser(user);
            }
        }
    }

    private void createUser(UserTO user) {
        try {
            userService.create(userMapper.toUserBO(user));
        } catch (UserAlreadyExistsException e1) {
            logger.error("User already exists! Should never happen while initiating mock data!");
        }
    }
}