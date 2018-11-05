package de.adorsys.ledgers.sca.service;

import de.adorsys.ledgers.sca.exception.*;
import de.adorsys.ledgers.um.api.domain.ScaUserDataBO;

public interface SCAOperationService {

	/**
	 * Generates an authentication code, hash it using the operation data and stores
	 * the hash for later verification.
	 *
	 * @param userLogin : This is login of the user in the bank system.
	 * @param scaMethod : This is user method for which OTP should be send
	 * @param opData : This are data to be linked to the generated One Time Password.
	 * @param userMessage : This is a message that will be send to the user.
	 * @param validitySeconds : documents the validity time of the generated OTP
	 * @return the generated AuthCode in clear text.
	 */
	String generateAuthCode(String userLogin, ScaUserDataBO scaMethod, String opData, String userMessage, int validitySeconds) throws AuthCodeGenerationException, SCAMethodNotSupportedException;

	/**
	 * Verify that the auth code, recomputing and verifying the hash of (Auth Code and opData).
	 *  
	 * @param opId : This is the id of the operation like provided by the consuming module.
	 * @param opData : This are data to be linked to the generated One Time Password.
	 * @param authCode : This auth code was generated at previous step @see #generateAuthCode(String opId, String opData, int validitySeconds)
	 * @return true if auth code is valid in other cases false will be returned
	 */
	boolean validateAuthCode(String opId, String opData, String authCode) throws SCAOperationNotFoundException, SCAOperationValidationException, SCAOperationUsedOrStolenException, SCAOperationExpiredException;


    /**
     * All operations that have status NEW will be changed on EXPIRED if date of creation + validitySeconds in the past
     */
	void processExpiredOperations();
}
