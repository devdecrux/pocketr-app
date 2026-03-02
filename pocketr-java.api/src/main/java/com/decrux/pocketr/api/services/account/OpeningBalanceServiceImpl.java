package com.decrux.pocketr.api.services.account;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.UserRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import com.decrux.pocketr.api.services.ledger.ManageLedger;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpeningBalanceServiceImpl implements OpeningBalanceService {

    private static final String OPENING_EQUITY_NAME = "Opening Equity";

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final ManageLedger manageLedger;
    private final OwnershipGuard ownershipGuard;

    public OpeningBalanceServiceImpl(
        AccountRepository accountRepository,
        UserRepository userRepository,
        ManageLedger manageLedger,
        OwnershipGuard ownershipGuard
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.manageLedger = manageLedger;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional
    public void createForNewAssetAccount(
        User owner,
        Account assetAccount,
        long openingBalanceMinor,
        LocalDate txnDate
    ) {
        long ownerId = requireNotNull(owner.getUserId(), "User ID must not be null");
        var assetAccountId = requireNotNull(assetAccount.getId(), "Account ID must not be null");
        Currency currency = requireNotNull(assetAccount.getCurrency(), "Currency must not be null");
        String currencyCode = currency.getCode();

        if (assetAccount.getType() != AccountType.ASSET) {
            throw new BadRequestException("Opening balance is supported only for ASSET accounts");
        }

        ownershipGuard.requireOwner(
            assetAccount.getOwner() != null ? assetAccount.getOwner().getUserId() : null,
            ownerId,
            "Not the owner of this account"
        );

        if (openingBalanceMinor == 0L) {
            throw new BadRequestException("openingBalanceMinor must not be zero");
        }
        if (openingBalanceMinor == Long.MIN_VALUE) {
            throw new BadRequestException("openingBalanceMinor is out of supported range");
        }

        Account openingEquity = getOrCreateOpeningEquityAccount(owner, currencyCode, currency);
        var openingEquityId = requireNotNull(openingEquity.getId(), "Opening equity account ID must not be null");
        long absoluteAmount = openingBalanceMinor > 0 ? openingBalanceMinor : -openingBalanceMinor;

        String assetSide = openingBalanceMinor > 0 ? "DEBIT" : "CREDIT";
        String equitySide = openingBalanceMinor > 0 ? "CREDIT" : "DEBIT";

        manageLedger.createTransaction(
            new CreateTransactionDto(
                "INDIVIDUAL",
                null,
                txnDate,
                currencyCode,
                "Opening balance - " + assetAccount.getName(),
                List.of(
                    new CreateSplitDto(assetAccountId, assetSide, absoluteAmount, null),
                    new CreateSplitDto(openingEquityId, equitySide, absoluteAmount, null)
                )
            ),
            owner
        );
    }

    private Account getOrCreateOpeningEquityAccount(User owner, String currencyCode, Currency currency) {
        long ownerId = requireNotNull(owner.getUserId(), "User ID must not be null");

        userRepository.findByUserIdForUpdate(ownerId).orElseThrow(() -> new NotFoundException("User not found"));

        Account existing = accountRepository.findByOwnerUserIdAndTypeAndCurrencyCodeAndName(
            ownerId,
            AccountType.EQUITY,
            currencyCode,
            OPENING_EQUITY_NAME
        );
        if (existing != null) {
            return existing;
        }

        Account account = new Account();
        account.setOwner(owner);
        account.setName(OPENING_EQUITY_NAME);
        account.setType(AccountType.EQUITY);
        account.setCurrency(currency);
        return accountRepository.save(account);
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
