package com.decrux.pocketr.api.services.account;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.Currency;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateAccountDto;
import com.decrux.pocketr.api.entities.dtos.UpdateAccountDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import com.decrux.pocketr.api.repositories.HouseholdAccountShareRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageAccountImpl implements ManageAccount {

    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final OpeningBalanceService openingBalanceService;
    private final ManageHousehold manageHousehold;
    private final HouseholdAccountShareRepository householdAccountShareRepository;
    private final OwnershipGuard ownershipGuard;

    public ManageAccountImpl(
        AccountRepository accountRepository,
        CurrencyRepository currencyRepository,
        OpeningBalanceService openingBalanceService,
        ManageHousehold manageHousehold,
        HouseholdAccountShareRepository householdAccountShareRepository,
        OwnershipGuard ownershipGuard
    ) {
        this.accountRepository = accountRepository;
        this.currencyRepository = currencyRepository;
        this.openingBalanceService = openingBalanceService;
        this.manageHousehold = manageHousehold;
        this.householdAccountShareRepository = householdAccountShareRepository;
        this.ownershipGuard = ownershipGuard;
    }

    @Override
    @Transactional
    public AccountDto createAccount(CreateAccountDto dto, User owner) {
        AccountType accountType;
        try {
            accountType = AccountType.valueOf(dto.getType());
        } catch (IllegalArgumentException ignored) {
            throw new BadRequestException("Invalid account type: " + dto.getType());
        }

        if (accountType == AccountType.EQUITY) {
            throw new BadRequestException("EQUITY accounts are system-managed and cannot be created manually");
        }

        Currency currency = currencyRepository
            .findById(dto.getCurrency())
            .orElseThrow(() -> new BadRequestException("Invalid currency: " + dto.getCurrency()));

        long openingBalanceMinor = dto.getOpeningBalanceMinor() != null ? dto.getOpeningBalanceMinor() : 0L;
        if (openingBalanceMinor != 0L && accountType != AccountType.ASSET) {
            throw new BadRequestException("openingBalanceMinor is supported only for ASSET accounts");
        }
        if (openingBalanceMinor == 0L && dto.getOpeningBalanceDate() != null) {
            throw new BadRequestException("openingBalanceDate requires non-zero openingBalanceMinor");
        }

        Account account = new Account();
        account.setOwner(owner);
        account.setName(dto.getName().trim());
        account.setType(accountType);
        account.setCurrency(currency);

        Account savedAccount = accountRepository.save(account);

        if (openingBalanceMinor != 0L) {
            openingBalanceService.createForNewAssetAccount(
                owner,
                savedAccount,
                openingBalanceMinor,
                dto.getOpeningBalanceDate() != null ? dto.getOpeningBalanceDate() : LocalDate.now()
            );
        }

        return toDto(savedAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> listIndividualAccounts(User owner) {
        long userId = requireNotNull(owner.getUserId(), "User ID must not be null");
        List<Account> accounts = accountRepository.findByOwnerUserId(userId);
        return accounts.stream().map(ManageAccountImpl::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountDto> listAccountsByMode(User user, String mode, UUID householdId) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");

        if ("INDIVIDUAL".equals(mode)) {
            return listIndividualAccounts(user);
        }

        if (!"HOUSEHOLD".equals(mode)) {
            throw new BadRequestException("Invalid mode: " + mode);
        }

        UUID hhId = householdId;
        if (hhId == null) {
            throw new BadRequestException("householdId is required for HOUSEHOLD mode");
        }

        if (!manageHousehold.isActiveMember(hhId, userId)) {
            throw new ForbiddenException("Not an active member of this household");
        }

        List<Account> ownedAccounts = accountRepository.findByOwnerUserId(userId);
        Set<UUID> sharedAccountIds = householdAccountShareRepository.findSharedAccountIdsByHouseholdId(hhId);
        List<Account> sharedAccounts = sharedAccountIds.isEmpty()
            ? List.of()
            : accountRepository.findAllById(sharedAccountIds);

        Set<UUID> seen = new HashSet<>();
        List<Account> merged = new ArrayList<>();
        List<Account> candidates = new ArrayList<>(ownedAccounts.size() + sharedAccounts.size());
        candidates.addAll(ownedAccounts);
        candidates.addAll(sharedAccounts);
        for (Account account : candidates) {
            UUID accountId = requireNotNull(account.getId(), "Account ID must not be null");
            if (seen.add(accountId)) {
                merged.add(account);
            }
        }

        return merged.stream().map(ManageAccountImpl::toDto).toList();
    }

    @Override
    @Transactional
    public AccountDto updateAccount(UUID id, UpdateAccountDto dto, User owner) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new NotFoundException("Account not found"));

        ownershipGuard.requireOwner(
            account.getOwner() != null ? account.getOwner().getUserId() : null,
            requireNotNull(owner.getUserId(), "User ID must not be null"),
            "Not the owner of this account"
        );

        if (dto.getName() != null) {
            account.setName(dto.getName().trim());
        }

        return toDto(accountRepository.save(account));
    }

    private static AccountDto toDto(Account account) {
        return new AccountDto(
            requireNotNull(account.getId(), "Account ID must not be null"),
            requireNotNull(account.getOwner() != null ? account.getOwner().getUserId() : null, "Owner user ID must not be null"),
            account.getName(),
            account.getType().name(),
            requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null, "Currency must not be null"),
            account.getCreatedAt()
        );
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
