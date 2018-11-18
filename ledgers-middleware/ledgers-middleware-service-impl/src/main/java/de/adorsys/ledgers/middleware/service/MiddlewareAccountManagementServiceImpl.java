package de.adorsys.ledgers.middleware.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.adorsys.ledgers.deposit.api.domain.BalanceBO;
import de.adorsys.ledgers.deposit.api.domain.DepositAccountDetailsBO;
import de.adorsys.ledgers.deposit.api.domain.TransactionDetailsBO;
import de.adorsys.ledgers.deposit.api.exception.DepositAccountNotFoundException;
import de.adorsys.ledgers.deposit.api.exception.TransactionNotFoundException;
import de.adorsys.ledgers.deposit.api.service.DepositAccountService;
import de.adorsys.ledgers.middleware.converter.AccountDetailsMapper;
import de.adorsys.ledgers.middleware.converter.PaymentConverter;
import de.adorsys.ledgers.middleware.service.domain.account.AccountBalanceTO;
import de.adorsys.ledgers.middleware.service.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.service.domain.account.TransactionTO;
import de.adorsys.ledgers.middleware.service.exception.AccountNotFoundMiddlewareException;
import de.adorsys.ledgers.middleware.service.exception.TransactionNotFoundMiddlewareException;
import de.adorsys.ledgers.middleware.service.exception.UserNotFoundMiddlewareException;
import de.adorsys.ledgers.um.api.domain.AccessTypeBO;
import de.adorsys.ledgers.um.api.domain.AccountAccessBO;
import de.adorsys.ledgers.um.api.domain.UserBO;
import de.adorsys.ledgers.um.api.exception.UserNotFoundException;
import de.adorsys.ledgers.um.api.service.UserService;

@Service
public class MiddlewareAccountManagementServiceImpl implements MiddlewareAccountManagementService {
    private static final Logger logger = LoggerFactory.getLogger(MiddlewareAccountManagementServiceImpl.class);
	
    private static final LocalDateTime BASE_TIME = LocalDateTime.MIN;

    @Autowired
	private DepositAccountService depositAccountService;
	
	@Autowired
	private AccountDetailsMapper accountDetailsMapper;

	@Autowired
    private PaymentConverter paymentConverter;

    @Autowired
    private UserService userService;
	
	@Override
	public void createDepositAccount(AccountDetailsTO depositAccount) throws AccountNotFoundMiddlewareException {
		try {
			depositAccountService.createDepositAccount(accountDetailsMapper.toDepositAccountBO(depositAccount));
		} catch (DepositAccountNotFoundException e) {
			logger.error(e.getMessage(), e);
			throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}		
	}

	@Override
	public AccountDetailsTO getDepositAccountById(String accountId, LocalDateTime time, boolean withBalance) throws AccountNotFoundMiddlewareException {
		DepositAccountDetailsBO accountDetailsBO;
		try {
			accountDetailsBO = depositAccountService.getDepositAccountById(accountId, time, withBalance);
		} catch (DepositAccountNotFoundException e) {
			logger.error(e.getMessage(), e);
			throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}
		return accountDetailsMapper.toAccountDetailsTO(accountDetailsBO.getAccount(), accountDetailsBO.getBalances());
	}

	@Override
	public AccountDetailsTO getDepositAccountByIBAN(String iban, LocalDateTime time, boolean withBalance) throws AccountNotFoundMiddlewareException {
		DepositAccountDetailsBO accountDetailsBO;
		try {
			accountDetailsBO = depositAccountService.getDepositAccountByIBAN(iban, time, withBalance);
		} catch (DepositAccountNotFoundException e) {
			logger.error(e.getMessage(), e);
			throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}
		return accountDetailsMapper.toAccountDetailsTO(accountDetailsBO.getAccount(), accountDetailsBO.getBalances());
	}

    @Override
    public AccountDetailsTO getAccountDetailsByAccountId(String accountId) throws AccountNotFoundMiddlewareException {
        try {
            DepositAccountDetailsBO accountDetailsBO = depositAccountService.getDepositAccountById(accountId, LocalDateTime.now(), true);
            List<BalanceBO> balances = accountDetailsBO.getBalances();
            return accountDetailsMapper.toAccountDetailsTO(accountDetailsBO.getAccount(), balances);
        } catch (DepositAccountNotFoundException e) {
            logger.error("Deposit Account with id=" + accountId + "not found", e);
            throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
        }
    }

    @Override
    public AccountDetailsTO getAccountDetailsByIban(String iban) throws AccountNotFoundMiddlewareException {
		try {
			DepositAccountDetailsBO depositAccountBO = depositAccountService.getDepositAccountByIBAN(iban, BASE_TIME, false);
			return accountDetailsMapper.toAccountDetailsTO(depositAccountBO);
		} catch (DepositAccountNotFoundException e) {
            logger.error("Deposit Account with iban={} not found", iban, e);
            throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}
    }

    @Override
    public AccountDetailsTO getAccountDetailsWithBalancesByIban(String iban, LocalDateTime refTime) throws AccountNotFoundMiddlewareException {
		try {
			DepositAccountDetailsBO depositAccountBO = depositAccountService.getDepositAccountByIBAN(iban, refTime, true);
			return accountDetailsMapper.toAccountDetailsTO(depositAccountBO);
		} catch (DepositAccountNotFoundException e) {
            logger.error("Deposit Account with iban={} not found", iban, e);
            throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}
    }
    
    @Override
    public List<AccountDetailsTO> getAllAccountDetailsByUserLogin(String userLogin) throws UserNotFoundMiddlewareException, AccountNotFoundMiddlewareException {
        logger.info("Retrieving accounts by user login {}", userLogin);
        try {
            UserBO userBO = userService.findByLogin(userLogin);
            List<AccountAccessBO> accountAccess = userBO.getAccountAccesses();
            logger.info("{} accounts were retrieved", accountAccess.size());

            List<String> ibans = accountAccess.stream()
                                         .filter(a -> a.getAccessType() == AccessTypeBO.OWNER)
                                         .map(AccountAccessBO::getIban)
                                         .collect(Collectors.toList());
            logger.info("{} were accounts were filtered as OWN", ibans.size());

            List<DepositAccountDetailsBO> depositAccounts = depositAccountService.getDepositAccountsByIBAN(ibans, BASE_TIME, false);
            logger.info("{} deposit accounts were found", depositAccounts.size());
            
            return depositAccounts.stream().map(accountDetailsMapper::toAccountDetailsTO).collect(Collectors.toList());
        } catch (UserNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new UserNotFoundMiddlewareException(e.getMessage());
        } catch (DepositAccountNotFoundException e) {
            logger.error(e.getMessage(), e);
			throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
		}
    }
    

    @Override
    public List<AccountBalanceTO> getBalances(String accountId) throws AccountNotFoundMiddlewareException {
        try {
            DepositAccountDetailsBO accountDetailsBO = depositAccountService.getDepositAccountById(accountId, LocalDateTime.now(), true);
            List<BalanceBO> balances = accountDetailsBO.getBalances();
            return accountDetailsMapper.toAccountBalancesTO(balances);
        } catch (DepositAccountNotFoundException e) {
            logger.error(e.getMessage(), e);
            throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
        }
    }

    @Override
    public TransactionTO getTransactionById(String accountId, String transactionId) throws TransactionNotFoundMiddlewareException {
        try {
            TransactionDetailsBO transaction = depositAccountService.getTransactionById(accountId, transactionId);
            return paymentConverter.toTransactionTO(transaction);
        } catch (TransactionNotFoundException e) {
            throw new TransactionNotFoundMiddlewareException(e.getMessage(), e);
        }

    }

    @Override
    public List<TransactionTO> getTransactionsByDates(String accountId, LocalDate dateFrom, LocalDate dateTo) throws AccountNotFoundMiddlewareException {
        LocalDate today = LocalDate.now();
        LocalDateTime dateTimeFrom = dateFrom == null
                                             ? today.atStartOfDay()
                                             : dateFrom.atStartOfDay();
        LocalDateTime dateTimeTo = dateTo == null
                                           ? today.atTime(LocalTime.MAX)
                                           : dateTo.atTime(LocalTime.MAX);
        try {
            List<TransactionDetailsBO> transactions = depositAccountService.getTransactionsByDates(accountId, dateTimeFrom, dateTimeTo);
            return paymentConverter.toTransactionTOList(transactions);
        } catch (DepositAccountNotFoundException e) {
            throw new AccountNotFoundMiddlewareException(e.getMessage(), e);
        }
    }
}
