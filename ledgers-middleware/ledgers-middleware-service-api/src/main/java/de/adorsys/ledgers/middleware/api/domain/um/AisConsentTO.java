/*
 * Copyright 2018-2018 adorsys GmbH & Co KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.adorsys.ledgers.middleware.api.domain.um;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Collections;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ais consent request", name = "AisConsentRequest")
public class AisConsentTO {

    @Schema(description = "The consent id", required = true)
    private String id;

    @Schema(description = "Corresponding PSU", required = true)
    private String userId;

    @Schema(description = "ID of the corresponding TPP.", required = true, example = "testTPP")
    private String tppId;

    @Schema(description = "Maximum frequency for an access per day. For a once-off access, this attribute is set to 1", required = true, example = "4")
    private int frequencyPerDay;

    @Schema(description = "Set of accesses given by psu for this account", required = true)
    private AisAccountAccessInfoTO access;

    @Schema(description = "Consent`s expiration date. The content is the local ASPSP date in ISODate Format", required = true, example = "2020-10-10")
    private LocalDate validUntil;

    @Schema(description = "'true', if the consent is for recurring access to the account data , 'false', if the consent is for one access to the account data", required = true, example = "false")
    private boolean recurringIndicator;

    @JsonIgnore
    public boolean isValidConsent() {
        return validUntil == null || validUntil.isAfter(LocalDate.now());
    }

    @JsonIgnore
    public void cleanupForPIIS() {
        // Remove all but balances from accesses.
        access.setAllPsd2(null);
        access.setAvailableAccounts(null);
        access.setAccounts(Collections.emptyList());
        access.setTransactions(Collections.emptyList());
    }
}
