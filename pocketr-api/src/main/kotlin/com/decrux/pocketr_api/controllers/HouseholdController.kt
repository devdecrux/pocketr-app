package com.decrux.pocketr_api.controllers

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.dtos.AccountDto
import com.decrux.pocketr_api.entities.dtos.CreateHouseholdDto
import com.decrux.pocketr_api.entities.dtos.HouseholdAccountShareDto
import com.decrux.pocketr_api.entities.dtos.HouseholdDto
import com.decrux.pocketr_api.entities.dtos.HouseholdMemberDto
import com.decrux.pocketr_api.entities.dtos.HouseholdSummaryDto
import com.decrux.pocketr_api.entities.dtos.InviteMemberDto
import com.decrux.pocketr_api.entities.dtos.ShareAccountDto
import com.decrux.pocketr_api.services.household.ManageHousehold
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/v1/households")
class HouseholdController(
    private val manageHousehold: ManageHousehold,
) {
    @GetMapping
    fun listHouseholds(
        @AuthenticationPrincipal user: User,
    ): List<HouseholdSummaryDto> = manageHousehold.listHouseholds(user)

    @GetMapping("/{id}")
    fun getHousehold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): HouseholdDto = manageHousehold.getHousehold(id, user)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHousehold(
        @RequestBody dto: CreateHouseholdDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdDto = manageHousehold.createHousehold(dto, user)

    @PostMapping("/{id}/invite")
    @ResponseStatus(HttpStatus.CREATED)
    fun inviteMember(
        @PathVariable id: UUID,
        @RequestBody dto: InviteMemberDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdMemberDto = manageHousehold.inviteMember(id, dto, user)

    @PostMapping("/{id}/accept-invite")
    fun acceptInvite(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): HouseholdMemberDto = manageHousehold.acceptInvite(id, user)

    @PostMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveHousehold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageHousehold.leaveHousehold(id, user)
    }

    @GetMapping("/{id}/shares")
    fun listSharedAccounts(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): List<HouseholdAccountShareDto> = manageHousehold.listSharedAccounts(id, user)

    @GetMapping("/{id}/accounts")
    fun listHouseholdAccounts(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: User,
    ): List<AccountDto> = manageHousehold.listHouseholdAccounts(id, user)

    @PostMapping("/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    fun shareAccount(
        @PathVariable id: UUID,
        @RequestBody dto: ShareAccountDto,
        @AuthenticationPrincipal user: User,
    ): HouseholdAccountShareDto = manageHousehold.shareAccount(id, dto, user)

    @DeleteMapping("/{id}/shares/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unshareAccount(
        @PathVariable id: UUID,
        @PathVariable accountId: UUID,
        @AuthenticationPrincipal user: User,
    ) {
        manageHousehold.unshareAccount(id, accountId, user)
    }
}
