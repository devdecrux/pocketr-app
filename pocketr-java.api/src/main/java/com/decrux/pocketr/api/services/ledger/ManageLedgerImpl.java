package com.decrux.pocketr.api.services.ledger;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.Account;
import com.decrux.pocketr.api.entities.db.ledger.AccountType;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.db.ledger.LedgerSplit;
import com.decrux.pocketr.api.entities.db.ledger.LedgerTxn;
import com.decrux.pocketr.api.entities.db.ledger.SplitSide;
import com.decrux.pocketr.api.entities.dtos.BalanceDto;
import com.decrux.pocketr.api.entities.dtos.CreateSplitDto;
import com.decrux.pocketr.api.entities.dtos.CreateTransactionDto;
import com.decrux.pocketr.api.entities.dtos.PagedTransactionsDto;
import com.decrux.pocketr.api.entities.dtos.SplitDto;
import com.decrux.pocketr.api.entities.dtos.TransactionDto;
import com.decrux.pocketr.api.entities.dtos.TxnCreatorDto;
import com.decrux.pocketr.api.exceptions.BadRequestException;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.exceptions.NotFoundException;
import com.decrux.pocketr.api.repositories.AccountRepository;
import com.decrux.pocketr.api.repositories.CategoryTagRepository;
import com.decrux.pocketr.api.repositories.CurrencyRepository;
import com.decrux.pocketr.api.repositories.LedgerSplitRepository;
import com.decrux.pocketr.api.repositories.LedgerTxnRepository;
import com.decrux.pocketr.api.services.household.ManageHousehold;
import com.decrux.pocketr.api.services.ledger.validations.CrossUserAssetAccountTypeValidator;
import com.decrux.pocketr.api.services.ledger.validations.DoubleEntryBalanceValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdIdPresenceValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdMembershipValidator;
import com.decrux.pocketr.api.services.ledger.validations.HouseholdSharedAccountValidator;
import com.decrux.pocketr.api.services.ledger.validations.IndividualModeOwnershipValidator;
import com.decrux.pocketr.api.services.ledger.validations.MinimumSplitCountValidator;
import com.decrux.pocketr.api.services.ledger.validations.PositiveSplitAmountValidator;
import com.decrux.pocketr.api.services.ledger.validations.SplitSideValueValidator;
import com.decrux.pocketr.api.services.ledger.validations.TransactionAccountCurrencyValidator;
import com.decrux.pocketr.api.services.user_avatar.UserAvatarService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageLedgerImpl implements ManageLedger {

    private static final Set<AccountType> DEBIT_NORMAL_TYPES = Set.of(AccountType.ASSET, AccountType.EXPENSE);
    private static final Set<AccountType> TRANSFER_TYPES = Set.of(
        AccountType.ASSET,
        AccountType.LIABILITY,
        AccountType.EQUITY
    );

    private final LedgerTxnRepository ledgerTxnRepository;
    private final LedgerSplitRepository ledgerSplitRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final CategoryTagRepository categoryTagRepository;
    private final ManageHousehold manageHousehold;
    private final UserAvatarService userAvatarService;
    private final MinimumSplitCountValidator minimumSplitCountValidator;
    private final PositiveSplitAmountValidator positiveSplitAmountValidator;
    private final SplitSideValueValidator splitSideValueValidator;
    private final DoubleEntryBalanceValidator doubleEntryBalanceValidator;
    private final TransactionAccountCurrencyValidator transactionAccountCurrencyValidator;
    private final IndividualModeOwnershipValidator individualModeOwnershipValidator;
    private final HouseholdIdPresenceValidator householdIdPresenceValidator;
    private final HouseholdMembershipValidator householdMembershipValidator;
    private final HouseholdSharedAccountValidator householdSharedAccountValidator;
    private final CrossUserAssetAccountTypeValidator crossUserAssetAccountTypeValidator;

    public ManageLedgerImpl(
        LedgerTxnRepository ledgerTxnRepository,
        LedgerSplitRepository ledgerSplitRepository,
        AccountRepository accountRepository,
        CurrencyRepository currencyRepository,
        CategoryTagRepository categoryTagRepository,
        ManageHousehold manageHousehold,
        UserAvatarService userAvatarService,
        MinimumSplitCountValidator minimumSplitCountValidator,
        PositiveSplitAmountValidator positiveSplitAmountValidator,
        SplitSideValueValidator splitSideValueValidator,
        DoubleEntryBalanceValidator doubleEntryBalanceValidator,
        TransactionAccountCurrencyValidator transactionAccountCurrencyValidator,
        IndividualModeOwnershipValidator individualModeOwnershipValidator,
        HouseholdIdPresenceValidator householdIdPresenceValidator,
        HouseholdMembershipValidator householdMembershipValidator,
        HouseholdSharedAccountValidator householdSharedAccountValidator,
        CrossUserAssetAccountTypeValidator crossUserAssetAccountTypeValidator
    ) {
        this.ledgerTxnRepository = ledgerTxnRepository;
        this.ledgerSplitRepository = ledgerSplitRepository;
        this.accountRepository = accountRepository;
        this.currencyRepository = currencyRepository;
        this.categoryTagRepository = categoryTagRepository;
        this.manageHousehold = manageHousehold;
        this.userAvatarService = userAvatarService;
        this.minimumSplitCountValidator = minimumSplitCountValidator;
        this.positiveSplitAmountValidator = positiveSplitAmountValidator;
        this.splitSideValueValidator = splitSideValueValidator;
        this.doubleEntryBalanceValidator = doubleEntryBalanceValidator;
        this.transactionAccountCurrencyValidator = transactionAccountCurrencyValidator;
        this.individualModeOwnershipValidator = individualModeOwnershipValidator;
        this.householdIdPresenceValidator = householdIdPresenceValidator;
        this.householdMembershipValidator = householdMembershipValidator;
        this.householdSharedAccountValidator = householdSharedAccountValidator;
        this.crossUserAssetAccountTypeValidator = crossUserAssetAccountTypeValidator;
    }

    @Override
    @Transactional
    public TransactionDto createTransaction(CreateTransactionDto dto, User creator) {
        long userId = requireNotNull(creator.getUserId(), "User ID must not be null");
        boolean isHouseholdMode =
            dto.getMode() != null && "HOUSEHOLD".equals(dto.getMode().toUpperCase(Locale.ROOT));

        minimumSplitCountValidator.validate(dto.getSplits());
        positiveSplitAmountValidator.validate(dto.getSplits());
        splitSideValueValidator.validate(dto.getSplits());
        doubleEntryBalanceValidator.validate(dto.getSplits());

        var currency = currencyRepository
            .findById(dto.getCurrency())
            .orElseThrow(() -> new BadRequestException("Invalid currency: " + dto.getCurrency()));

        List<UUID> accountIds = dto.getSplits().stream().map(CreateSplitDto::getAccountId).distinct().toList();
        List<Account> accounts = accountRepository.findAllById(accountIds);
        if (accounts.size() != accountIds.size()) {
            Set<UUID> foundIds = accounts.stream().map(Account::getId).collect(Collectors.toSet());
            List<UUID> missingIds = accountIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new BadRequestException("Accounts not found: " + missingIds);
        }

        Map<UUID, Account> accountMap = accounts
            .stream()
            .collect(Collectors.toMap(account -> requireNotNull(account.getId()), Function.identity()));

        transactionAccountCurrencyValidator.validate(accounts, dto.getCurrency());

        List<Account> nonOwnedAccounts = accounts
            .stream()
            .filter(account -> !Objects.equals(account.getOwner() != null ? account.getOwner().getUserId() : null, userId))
            .toList();

        if (!nonOwnedAccounts.isEmpty()) {
            individualModeOwnershipValidator.validate(nonOwnedAccounts, isHouseholdMode);
            UUID householdId = householdIdPresenceValidator.validate(dto.getHouseholdId());
            householdMembershipValidator.validate(manageHousehold, householdId, userId);
            householdSharedAccountValidator.validate(nonOwnedAccounts, manageHousehold, householdId);
            crossUserAssetAccountTypeValidator.validate(accounts);
        }

        List<UUID> categoryTagIds = dto
            .getSplits()
            .stream()
            .map(CreateSplitDto::getCategoryTagId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        Map<UUID, CategoryTag> categoryTagMap;
        if (!categoryTagIds.isEmpty()) {
            List<CategoryTag> tags = categoryTagRepository.findAllById(categoryTagIds);
            if (tags.size() != categoryTagIds.size()) {
                Set<UUID> foundIds = tags.stream().map(CategoryTag::getId).collect(Collectors.toSet());
                List<UUID> missingIds = categoryTagIds.stream().filter(id -> !foundIds.contains(id)).toList();
                throw new BadRequestException("Category tags not found: " + missingIds);
            }

            for (CategoryTag tag : tags) {
                if (!Objects.equals(tag.getOwner() != null ? tag.getOwner().getUserId() : null, userId)) {
                    throw new ForbiddenException("Category tag '" + tag.getName() + "' is not owned by current user");
                }
            }

            categoryTagMap = tags
                .stream()
                .collect(Collectors.toMap(tag -> requireNotNull(tag.getId()), Function.identity()));
        } else {
            categoryTagMap = Map.of();
        }

        LedgerTxn txn = new LedgerTxn();
        txn.setCreatedBy(creator);
        txn.setHouseholdId(isHouseholdMode ? dto.getHouseholdId() : null);
        txn.setTxnDate(dto.getTxnDate());
        txn.setDescription(dto.getDescription().trim());
        txn.setCurrency(currency);

        List<LedgerSplit> splits = dto.getSplits().stream().map(splitDto -> {
            LedgerSplit split = new LedgerSplit();
            split.setTransaction(txn);
            split.setAccount(accountMap.get(splitDto.getAccountId()));
            split.setSide(SplitSide.valueOf(splitDto.getSide()));
            split.setAmountMinor(splitDto.getAmountMinor());
            split.setCategoryTag(splitDto.getCategoryTagId() != null ? categoryTagMap.get(splitDto.getCategoryTagId()) : null);
            return split;
        }).toList();

        txn.setSplits(new ArrayList<>(splits));

        LedgerTxn savedTxn = ledgerTxnRepository.save(txn);
        return toDto(savedTxn, userAvatarService);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedTransactionsDto listTransactions(
        User user,
        String mode,
        UUID householdId,
        LocalDate dateFrom,
        LocalDate dateTo,
        UUID accountId,
        UUID categoryId,
        int page,
        int size
    ) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        boolean isHouseholdMode = mode != null && "HOUSEHOLD".equals(mode.toUpperCase(Locale.ROOT));

        Specification<LedgerTxn> spec;
        if (isHouseholdMode) {
            UUID hhId = householdId;
            if (hhId == null) {
                throw new BadRequestException("householdId is required for household mode");
            }
            if (!manageHousehold.isActiveMember(hhId, userId)) {
                throw new ForbiddenException("Not an active member of this household");
            }
            Set<UUID> sharedAccountIds = manageHousehold.getSharedAccountIds(hhId);
            if (sharedAccountIds.isEmpty()) {
                return new PagedTransactionsDto(List.of(), page, size, 0L, 0);
            }
            spec = LedgerTxnSpecs.hasAnySharedAccount(sharedAccountIds);
        } else {
            spec = LedgerTxnSpecs.forUser(userId);
        }

        if (dateFrom != null) {
            spec = spec.and(LedgerTxnSpecs.dateFrom(dateFrom));
        }
        if (dateTo != null) {
            spec = spec.and(LedgerTxnSpecs.dateTo(dateTo));
        }
        if (accountId != null) {
            spec = spec.and(LedgerTxnSpecs.hasAccount(accountId));
        }
        if (categoryId != null) {
            spec = spec.and(LedgerTxnSpecs.hasCategory(categoryId));
        }

        PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "txnDate").and(Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        Page<LedgerTxn> pageResult = ledgerTxnRepository.findAll(spec, pageable);

        return new PagedTransactionsDto(
            pageResult.getContent().stream().map(txn -> toDto(txn, userAvatarService)).toList(),
            pageResult.getNumber(),
            pageResult.getSize(),
            pageResult.getTotalElements(),
            pageResult.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceDto> getAccountBalances(List<UUID> accountIds, LocalDate asOf, User user, UUID householdId) {
        if (accountIds.isEmpty()) {
            return List.of();
        }

        long userId = requireNotNull(user.getUserId(), "User ID must not be null");
        List<UUID> uniqueAccountIds = accountIds.stream().distinct().toList();
        List<Account> accounts = accountRepository.findAllById(uniqueAccountIds);
        if (accounts.size() != uniqueAccountIds.size()) {
            throw new NotFoundException("Account not found");
        }

        if (householdId != null) {
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw new ForbiddenException("Not an active member of this household");
            }
            Set<UUID> sharedAccountIds = manageHousehold.getSharedAccountIds(householdId);
            if (uniqueAccountIds.stream().anyMatch(id -> !sharedAccountIds.contains(id))) {
                throw new ForbiddenException("Account is not shared into this household");
            }
        } else {
            boolean hasNonOwned = accounts
                .stream()
                .anyMatch(account -> !Objects.equals(account.getOwner() != null ? account.getOwner().getUserId() : null, userId));
            if (hasNonOwned) {
                throw new ForbiddenException("Not the owner of this account");
            }
        }

        Map<UUID, Long> rawBalancesByAccountId = ledgerSplitRepository
            .computeRawBalancesByAccountIds(uniqueAccountIds, asOf, SplitSide.DEBIT, SplitSide.CREDIT)
            .stream()
            .collect(Collectors.toMap(balance -> balance.getAccountId(), balance -> balance.getRawBalance()));

        Map<UUID, Account> accountById = accounts
            .stream()
            .collect(Collectors.toMap(account -> requireNotNull(account.getId()), Function.identity()));

        return uniqueAccountIds.stream().map(accountId -> {
            Account account = accountById.get(accountId);
            long rawBalance = rawBalancesByAccountId.getOrDefault(accountId, 0L);
            long balanceMinor = DEBIT_NORMAL_TYPES.contains(account.getType()) ? rawBalance : -rawBalance;

            return new BalanceDto(
                accountId,
                account.getName(),
                account.getType().name(),
                requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null),
                balanceMinor,
                asOf
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceDto getAccountBalance(UUID accountId, LocalDate asOf, User user, UUID householdId) {
        long userId = requireNotNull(user.getUserId(), "User ID must not be null");

        Account account = accountRepository.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found"));

        if (householdId != null) {
            if (!manageHousehold.isActiveMember(householdId, userId)) {
                throw new ForbiddenException("Not an active member of this household");
            }
            if (!manageHousehold.isAccountShared(householdId, accountId)) {
                throw new ForbiddenException("Account is not shared into this household");
            }
        } else if (!Objects.equals(account.getOwner() != null ? account.getOwner().getUserId() : null, userId)) {
            throw new ForbiddenException("Not the owner of this account");
        }

        boolean isDebitNormal = Set.of(AccountType.ASSET, AccountType.EXPENSE).contains(account.getType());
        long balanceMinor = isDebitNormal
            ? ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.DEBIT, SplitSide.CREDIT)
            : ledgerSplitRepository.computeBalance(accountId, asOf, SplitSide.CREDIT, SplitSide.DEBIT);

        return new BalanceDto(
            requireNotNull(account.getId()),
            account.getName(),
            account.getType().name(),
            requireNotNull(account.getCurrency() != null ? account.getCurrency().getCode() : null),
            balanceMinor,
            asOf
        );
    }

    private static TransactionDto toDto(LedgerTxn txn, UserAvatarService avatarService) {
        List<LedgerSplit> splits = txn.getSplits() != null ? txn.getSplits() : List.of();
        List<SplitDto> splitDtos = splits.stream().map(ManageLedgerImpl::toDto).toList();

        TxnCreatorDto createdByDto = null;
        if (txn.getCreatedBy() != null) {
            createdByDto = new TxnCreatorDto(
                txn.getCreatedBy().getFirstName(),
                txn.getCreatedBy().getLastName(),
                txn.getCreatedBy().getEmail(),
                avatarService.resolveAvatarDataUrl(txn.getCreatedBy().getAvatarPath())
            );
        }

        return new TransactionDto(
            requireNotNull(txn.getId(), "Transaction ID must not be null"),
            txn.getTxnDate(),
            requireNotNull(txn.getCurrency() != null ? txn.getCurrency().getCode() : null, "Currency must not be null"),
            txn.getDescription(),
            txn.getHouseholdId(),
            deriveTxnKind(splits),
            createdByDto,
            splitDtos,
            txn.getCreatedAt(),
            txn.getUpdatedAt()
        );
    }

    private static String deriveTxnKind(List<LedgerSplit> splits) {
        Set<AccountType> accountTypes = splits
            .stream()
            .map(split -> split.getAccount() != null ? split.getAccount().getType() : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(HashSet::new));

        if (accountTypes.stream().allMatch(TRANSFER_TYPES::contains)) {
            return "TRANSFER";
        }
        if (accountTypes.stream().anyMatch(type -> type == AccountType.EXPENSE)) {
            return "EXPENSE";
        }
        if (accountTypes.stream().anyMatch(type -> type == AccountType.INCOME)) {
            return "INCOME";
        }
        return "TRANSFER";
    }

    private static SplitDto toDto(LedgerSplit split) {
        AccountType accountType = requireNotNull(
            split.getAccount() != null ? split.getAccount().getType() : null,
            "Account type must not be null"
        );
        boolean isDebitNormal = DEBIT_NORMAL_TYPES.contains(accountType);
        boolean increases =
            (isDebitNormal && split.getSide() == SplitSide.DEBIT)
                || (!isDebitNormal && split.getSide() == SplitSide.CREDIT);
        long effectMinor = increases ? split.getAmountMinor() : -split.getAmountMinor();

        return new SplitDto(
            requireNotNull(split.getId(), "Split ID must not be null"),
            requireNotNull(split.getAccount() != null ? split.getAccount().getId() : null, "Account must not be null"),
            requireNotNull(
                split.getAccount() != null ? split.getAccount().getName() : null,
                "Account name must not be null"
            ),
            accountType.name(),
            split.getSide().name(),
            split.getAmountMinor(),
            effectMinor,
            split.getCategoryTag() != null ? split.getCategoryTag().getId() : null,
            split.getCategoryTag() != null ? split.getCategoryTag().getName() : null
        );
    }

    private static <T> T requireNotNull(T value) {
        return requireNotNull(value, "Required value was null.");
    }

    private static <T> T requireNotNull(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
