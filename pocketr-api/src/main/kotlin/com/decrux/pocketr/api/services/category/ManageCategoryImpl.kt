package com.decrux.pocketr.api.services.category

import com.decrux.pocketr.api.entities.db.auth.User
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag
import com.decrux.pocketr.api.entities.dtos.CategoryDto
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto
import com.decrux.pocketr.api.repositories.CategoryTagRepository
import com.decrux.pocketr.api.services.OwnershipGuard
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ManageCategoryImpl(
    private val categoryTagRepository: CategoryTagRepository,
    private val ownershipGuard: OwnershipGuard,
) : ManageCategory {
    @Transactional
    override fun createCategory(
        dto: CreateCategoryDto,
        owner: User,
    ): CategoryDto {
        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        val name = dto.name.trim()

        if (categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(userId, name)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Category '$name' already exists")
        }

        val tag =
            CategoryTag(
                owner = owner,
                name = name,
                color = dto.color?.takeIf { it.isNotBlank() },
            )

        try {
            return categoryTagRepository.save(tag).toDto()
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Category '$name' already exists")
        }
    }

    @Transactional(readOnly = true)
    override fun listCategories(owner: User): List<CategoryDto> {
        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        return categoryTagRepository.findByOwnerUserId(userId).map { it.toDto() }
    }

    @Transactional
    override fun updateCategory(
        id: UUID,
        dto: UpdateCategoryDto,
        owner: User,
    ): CategoryDto {
        val tag =
            categoryTagRepository
                .findById(id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found") }

        ownershipGuard.requireOwner(tag.owner?.userId, requireNotNull(owner.userId), "Not the owner of this category")

        val userId = requireNotNull(owner.userId) { "User ID must not be null" }
        val newName = dto.name.trim()

        if (!newName.equals(tag.name, ignoreCase = true) &&
            categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(userId, newName)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Category '$newName' already exists")
        }

        tag.name = newName
        tag.color = dto.color?.takeIf { it.isNotBlank() }
        try {
            return categoryTagRepository.save(tag).toDto()
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Category '$newName' already exists")
        }
    }

    @Transactional
    override fun deleteCategory(
        id: UUID,
        owner: User,
    ) {
        val tag =
            categoryTagRepository
                .findById(id)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found") }

        ownershipGuard.requireOwner(tag.owner?.userId, requireNotNull(owner.userId), "Not the owner of this category")

        try {
            categoryTagRepository.delete(tag)
            categoryTagRepository.flush()
        } catch (_: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Category is in use and cannot be deleted")
        }
    }

    private companion object {
        fun CategoryTag.toDto() =
            CategoryDto(
                id = requireNotNull(id) { "Category ID must not be null" },
                name = name,
                color = color,
                createdAt = createdAt,
            )
    }
}
