package de.adorsys.ledgers.middleware.impl.service;

import de.adorsys.ledgers.deposit.api.service.DepositAccountInitService;
import de.adorsys.ledgers.middleware.api.service.AppManagementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@Transactional
public class AppManagementServiceImpl implements AppManagementService {

    private final DepositAccountInitService depositAccountInitService;

    public AppManagementServiceImpl(DepositAccountInitService depositAccountInitService) {
		this.depositAccountInitService = depositAccountInitService;
	}

	@Override
	public void initApp() throws IOException {
		// Init deposit account config  data.
		depositAccountInitService.initConfigData();
	}

}