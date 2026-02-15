package com.decrux.pocketr_api.services.category

import com.decrux.pocketr_api.entities.db.auth.User
import com.decrux.pocketr_api.entities.db.ledger.CategoryTag
import com.decrux.pocketr_api.entities.dtos.CreateCategoryDto
import com.decrux.pocketr_api.entities.dtos.UpdateCategoryDto
import com.decrux.pocketr_api.repositories.CategoryTagRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

@DisplayName("ManageCategoryImpl")
class ManageCategoryImplTest {

    private lateinit var categoryTagRepository: CategoryTagRepository
    private lateinit var service: ManageCategoryImpl

    private val ownerUser = User(
        userId = 1L,
        passwordValue = "encoded",
        email = "alice@example.com",
    )

    private val otherUser = User(
        userId = 2L,
        passwordValue = "encoded",
        email = "bob@example.com",
    )

    @BeforeEach
    fun setUp() {
        categoryTagRepository = mock(CategoryTagRepository::class.java)
        service = ManageCategoryImpl(categoryTagRepository)
    }

    @Nested
    @DisplayName("createCategory")
    inner class CreateCategory {

        @Test
        @DisplayName("should create category with unique name")
        fun createCategoryWithUniqueName() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { invocation ->
                val tag = invocation.getArgument<CategoryTag>(0)
                tag.id = UUID.randomUUID()
                tag
            }

            val result = service.createCategory(CreateCategoryDto(name = "Groceries"), ownerUser)
            assertEquals("Groceries", result.name)
            assertNotNull(result.id)
        }

        @Test
        @DisplayName("should reject duplicate category name for same user")
        fun rejectDuplicateNameForSameUser() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(true)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createCategory(CreateCategoryDto(name = "Groceries"), ownerUser)
            }
            assertEquals(409, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("already exists"))
        }

        @Test
        @DisplayName("should reject duplicate category name case-insensitively")
        fun rejectDuplicateNameCaseInsensitive() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "groceries")).thenReturn(true)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.createCategory(CreateCategoryDto(name = "groceries"), ownerUser)
            }
            assertEquals(409, ex.statusCode.value())
            assertTrue(ex.reason!!.contains("already exists"))
        }

        @Test
        @DisplayName("should allow same category name for different users")
        fun allowSameNameForDifferentUsers() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false)
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(2L, "Groceries")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { invocation ->
                val tag = invocation.getArgument<CategoryTag>(0)
                tag.id = UUID.randomUUID()
                tag
            }

            val result1 = service.createCategory(CreateCategoryDto(name = "Groceries"), ownerUser)
            val result2 = service.createCategory(CreateCategoryDto(name = "Groceries"), otherUser)
            assertEquals("Groceries", result1.name)
            assertEquals("Groceries", result2.name)
        }

        @Test
        @DisplayName("should trim whitespace from category name")
        fun trimWhitespaceFromName() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { invocation ->
                val tag = invocation.getArgument<CategoryTag>(0)
                tag.id = UUID.randomUUID()
                tag
            }

            val result = service.createCategory(CreateCategoryDto(name = "  Groceries  "), ownerUser)
            assertEquals("Groceries", result.name)
        }

        @Test
        @DisplayName("should set owner to the authenticated user")
        fun setOwnerToAuthenticatedUser() {
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { invocation ->
                val tag = invocation.getArgument<CategoryTag>(0)
                tag.id = UUID.randomUUID()
                assertEquals(ownerUser.userId, tag.owner?.userId)
                tag
            }

            service.createCategory(CreateCategoryDto(name = "Food"), ownerUser)
            verify(categoryTagRepository).save(any(CategoryTag::class.java))
        }
    }

    @Nested
    @DisplayName("listCategories")
    inner class ListCategories {

        @Test
        @DisplayName("should return all categories for owner")
        fun returnAllCategoriesForOwner() {
            val tags = listOf(
                CategoryTag(id = UUID.randomUUID(), owner = ownerUser, name = "Groceries"),
                CategoryTag(id = UUID.randomUUID(), owner = ownerUser, name = "Electricity"),
            )
            `when`(categoryTagRepository.findByOwnerUserId(1L)).thenReturn(tags)

            val result = service.listCategories(ownerUser)
            assertEquals(2, result.size)
            assertEquals("Groceries", result[0].name)
            assertEquals("Electricity", result[1].name)
        }

        @Test
        @DisplayName("should return empty list when user has no categories")
        fun returnEmptyListWhenNoCategories() {
            `when`(categoryTagRepository.findByOwnerUserId(1L)).thenReturn(emptyList())

            val result = service.listCategories(ownerUser)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("updateCategory")
    inner class UpdateCategory {

        private val categoryId = UUID.randomUUID()
        private lateinit var existingTag: CategoryTag

        @BeforeEach
        fun setUp() {
            existingTag = CategoryTag(id = categoryId, owner = ownerUser, name = "Groceries")
        }

        @Test
        @DisplayName("should rename category")
        fun renameCategory() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { it.getArgument<CategoryTag>(0) }

            val result = service.updateCategory(categoryId, UpdateCategoryDto(name = "Food"), ownerUser)
            assertEquals("Food", result.name)
        }

        @Test
        @DisplayName("should reject rename to existing name for same user")
        fun rejectRenameToDuplicateName() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Electricity")).thenReturn(true)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateCategory(categoryId, UpdateCategoryDto(name = "Electricity"), ownerUser)
            }
            assertEquals(409, ex.statusCode.value())
        }

        @Test
        @DisplayName("should reject rename to existing name case-insensitively")
        fun rejectRenameToDuplicateNameCaseInsensitive() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "electricity")).thenReturn(true)

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateCategory(categoryId, UpdateCategoryDto(name = "electricity"), ownerUser)
            }
            assertEquals(409, ex.statusCode.value())
        }

        @Test
        @DisplayName("should allow renaming to the same name (no-op)")
        fun allowRenamingToSameName() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { it.getArgument<CategoryTag>(0) }

            val result = service.updateCategory(categoryId, UpdateCategoryDto(name = "Groceries"), ownerUser)
            assertEquals("Groceries", result.name)
            // Should NOT check duplicate existence when name hasn't changed
        }

        @Test
        @DisplayName("should reject update by non-owner")
        fun rejectUpdateByNonOwner() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateCategory(categoryId, UpdateCategoryDto(name = "Stolen"), otherUser)
            }
            assertEquals(403, ex.statusCode.value())
        }

        @Test
        @DisplayName("should return 404 for non-existent category")
        fun notFoundForMissingCategory() {
            val missingId = UUID.randomUUID()
            `when`(categoryTagRepository.findById(missingId)).thenReturn(Optional.empty())

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.updateCategory(missingId, UpdateCategoryDto(name = "X"), ownerUser)
            }
            assertEquals(404, ex.statusCode.value())
        }

        @Test
        @DisplayName("should trim whitespace when renaming")
        fun trimWhitespaceOnRename() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))
            `when`(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false)
            `when`(categoryTagRepository.save(any(CategoryTag::class.java))).thenAnswer { it.getArgument<CategoryTag>(0) }

            val result = service.updateCategory(categoryId, UpdateCategoryDto(name = "  Food  "), ownerUser)
            assertEquals("Food", result.name)
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    inner class DeleteCategory {

        private val categoryId = UUID.randomUUID()
        private lateinit var existingTag: CategoryTag

        @BeforeEach
        fun setUp() {
            existingTag = CategoryTag(id = categoryId, owner = ownerUser, name = "Groceries")
        }

        @Test
        @DisplayName("should delete category owned by user")
        fun deleteCategoryOwnedByUser() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))

            service.deleteCategory(categoryId, ownerUser)
            verify(categoryTagRepository).delete(existingTag)
        }

        @Test
        @DisplayName("should reject delete by non-owner")
        fun rejectDeleteByNonOwner() {
            `when`(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag))

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.deleteCategory(categoryId, otherUser)
            }
            assertEquals(403, ex.statusCode.value())
            verify(categoryTagRepository, never()).delete(any())
        }

        @Test
        @DisplayName("should return 404 for non-existent category")
        fun notFoundForMissingCategory() {
            val missingId = UUID.randomUUID()
            `when`(categoryTagRepository.findById(missingId)).thenReturn(Optional.empty())

            val ex = assertThrows(ResponseStatusException::class.java) {
                service.deleteCategory(missingId, ownerUser)
            }
            assertEquals(404, ex.statusCode.value())
        }
    }
}
