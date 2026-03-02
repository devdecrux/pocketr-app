package com.decrux.pocketr.api.services.account;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import java.time.LocalDate;

public interface OpeningBalanceService {

    void createForNewAssetAccount(
        User owner,
        Account assetAccount,
        long openingBalanceMinor,
        LocalDate txnDate
    );
}
