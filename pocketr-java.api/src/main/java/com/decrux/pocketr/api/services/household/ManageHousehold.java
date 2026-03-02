package com.decrux.pocketr.api.services.household;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.dtos.AccountDto;
import com.decrux.pocketr.api.entities.dtos.CreateHouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdAccountShareDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdMemberDto;
import com.decrux.pocketr.api.entities.dtos.HouseholdSummaryDto;
import com.decrux.pocketr.api.entities.dtos.InviteMemberDto;
import com.decrux.pocketr.api.entities.dtos.ShareAccountDto;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ManageHousehold {

    HouseholdDto createHousehold(CreateHouseholdDto dto, User creator);

    List<HouseholdSummaryDto> listHouseholds(User user);

    HouseholdDto getHousehold(UUID id, User user);

    HouseholdMemberDto inviteMember(UUID householdId, InviteMemberDto dto, User inviter);

    HouseholdMemberDto acceptInvite(UUID householdId, User user);

    void leaveHousehold(UUID householdId, User user);

    HouseholdAccountShareDto shareAccount(UUID householdId, ShareAccountDto dto, User user);

    void unshareAccount(UUID householdId, UUID accountId, User user);

    List<HouseholdAccountShareDto> listSharedAccounts(UUID householdId, User user);

    List<AccountDto> listHouseholdAccounts(UUID householdId, User user);

    boolean isActiveMember(UUID householdId, long userId);

    boolean isAccountShared(UUID householdId, UUID accountId);

    Set<UUID> getSharedAccountIds(UUID householdId);
}
