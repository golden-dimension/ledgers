package de.adorsys.ledgers.middleware.api.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import de.adorsys.ledgers.middleware.api.domain.account.AccountDetailsTO;
import de.adorsys.ledgers.middleware.api.domain.account.FundsConfirmationRequestTO;
import de.adorsys.ledgers.middleware.api.domain.account.TransactionTO;
import de.adorsys.ledgers.middleware.api.domain.um.AccountAccessTO;
import de.adorsys.ledgers.middleware.api.exception.AccountNotFoundMiddlewareException;
import de.adorsys.ledgers.middleware.api.exception.AccountWithPrefixGoneMiddlewareException;
import de.adorsys.ledgers.middleware.api.exception.AccountWithSuffixExistsMiddlewareException;
import de.adorsys.ledgers.middleware.api.exception.InsufficientPermissionMiddlewareException;
import de.adorsys.ledgers.middleware.api.exception.TransactionNotFoundMiddlewareException;
import de.adorsys.ledgers.middleware.api.exception.UserNotFoundMiddlewareException;

public interface MiddlewareAccountManagementService {

    /**
     * Creates a new DepositAccount. This deposit account is then linked with the specified user.
     * 
     * Call requires a bank staff access permission.
     * 
     * @param depositAccount
	 * @throws AccountWithPrefixGoneMiddlewareException
	 * @throws AccountWithSuffixExistsMiddlewareException
     * @throws UserNotFoundMiddlewareException 
     */
	void createDepositAccount(AccountDetailsTO depositAccount) 
			throws AccountWithPrefixGoneMiddlewareException, AccountWithSuffixExistsMiddlewareException, UserNotFoundMiddlewareException;

    /**
     * Creates a new DepositAccount. This deposit account is then linked with the specified user.
     * 
     * Call requires a bank staff access permission.
     * 
     * @param accountAccesss : define who has access to the account.
     * @param depositAccount
	 * @throws AccountWithPrefixGoneMiddlewareException
	 * @throws AccountWithSuffixExistsMiddlewareException
     * @throws UserNotFoundMiddlewareException 
     */
	void createDepositAccount(AccountDetailsTO depositAccount, List<AccountAccessTO> accountAccesss) 
			throws AccountWithPrefixGoneMiddlewareException, AccountWithSuffixExistsMiddlewareException, UserNotFoundMiddlewareException;

	/**
	 * Creates a new DepositAccount for the connected user.
	 * 
	 * @param accountNumberPrefix
	 * @param accountNumberSuffix
	 * @throws AccountWithPrefixGoneMiddlewareException
	 * @throws AccountWithSuffixExistsMiddlewareException
	 */
	void createDepositAccount(String accountNumberPrefix, String accountNumberSuffix)
			throws AccountWithPrefixGoneMiddlewareException, AccountWithSuffixExistsMiddlewareException;
	

	/**
	 * Provide account access to another user of the system. In order to execute
	 * this operation, the connected user has to be the owner of the corresponding
	 * account.
	 * 
	 * @param accountAccess
     * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked ot the account.
	 */
	void grantAccessToDepositAccount(AccountAccessTO accountAccess)
			throws AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;
	
	/**
	 * Provide a third party provider with necessary permission to read accounts and
	 * transaction informations for the specified account.
	 * 
	 * @param accountAccess
	 * @param fromTime
	 * @param toTime
	 * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException
	 */
	void grantDeferedThirdPartyReadAccessToDepositAccount(AccountAccessTO accountAccess, LocalDateTime fromTime, LocalDateTime toTime)
			throws AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

	/**
	 * Retrieve the list of account viewable by the connected user.
	 * 
	 * @return an empty list if user not linked with any deposit accounted.
	 */
	List<AccountDetailsTO> listOfDepositAccounts();
	
    /**
     * Retrieves AccountDetails with Balance on demand
     * @param Id DepositAccount identifier
     * @param time
     * @param withBalance boolean specifying if Balances has to be added to AccountDetails
     * @return
     * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked ot the account.
     */
	AccountDetailsTO getDepositAccountById(String Id, LocalDateTime time, boolean withBalance) throws AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

    /**
     * Retrieves AccountDetails with Balance on demand
     * @param iban
     * @param time
     * @param withBalance
     * @return
     * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked ot the account.
     */
	AccountDetailsTO getDepositAccountByIban(String iban, LocalDateTime time, boolean withBalance)
			throws AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

    //============================ Account Details ==============================//
    /**
     * Retrieves a List of AccountDetails by user login (psuId)
     * @param userLogin
     * @return
     * @throws UserNotFoundMiddlewareException
     * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked ot the account.
     */
    List<AccountDetailsTO> getAllAccountDetailsByUserLogin(String userLogin) throws UserNotFoundMiddlewareException, AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

    /**
     * Retrieves transaction by accountId and transactionId
     * @param accountId
     * @param transactionId
     * @return
     * @throws AccountNotFoundMiddlewareException
     * @throws TransactionNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked ot the account.
     */
    TransactionTO getTransactionById(String accountId, String transactionId) throws AccountNotFoundMiddlewareException, TransactionNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

    /**
     * Retrieves a List of transactions by accountId and dates (from/to) if dateTo is empty it is considered that requested date is today
     * @param accountId
     * @param dateFrom
     * @param dateTo
     * @return
     * @throws AccountNotFoundMiddlewareException
	 * @throws InsufficientPermissionMiddlewareException : if the connected user is not linked of the account.
     */
    List<TransactionTO> getTransactionsByDates(String accountId, LocalDate dateFrom, LocalDate dateTo) throws AccountNotFoundMiddlewareException, InsufficientPermissionMiddlewareException;

    /**
     * Confirm the availability of funds on user account to perform the operation with specified amount
     * @param request
     * @return
     * @throws AccountNotFoundMiddlewareException
     */
    boolean confirmFundsAvailability(FundsConfirmationRequestTO request) throws AccountNotFoundMiddlewareException;
}
