package com.decrux.pocketr.api.services.category;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.decrux.pocketr.api.entities.db.auth.User;
import com.decrux.pocketr.api.entities.db.ledger.CategoryTag;
import com.decrux.pocketr.api.entities.dtos.CreateCategoryDto;
import com.decrux.pocketr.api.entities.dtos.UpdateCategoryDto;
import com.decrux.pocketr.api.exceptions.ForbiddenException;
import com.decrux.pocketr.api.repositories.CategoryTagRepository;
import com.decrux.pocketr.api.services.OwnershipGuard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ManageCategoryImpl")
class ManageCategoryImplTest {

    private CategoryTagRepository categoryTagRepository;
    private ManageCategoryImpl service;

    private User ownerUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        categoryTagRepository = mock(CategoryTagRepository.class);
        service = new ManageCategoryImpl(categoryTagRepository, new OwnershipGuard());
        ownerUser = createUser(1L, "alice@example.com");
        otherUser = createUser(2L, "bob@example.com");
    }

    @Nested
    @DisplayName("createCategory")
    class CreateCategory {

        @Test
        @DisplayName("should create category with unique name")
        void createCategoryWithUniqueName() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> {
                CategoryTag tag = invocation.getArgument(0);
                tag.setId(UUID.randomUUID());
                return tag;
            });

            var result = service.createCategory(new CreateCategoryDto("Groceries", null), ownerUser);
            assertEquals("Groceries", result.getName());
            assertNotNull(result.getId());
        }

        @Test
        @DisplayName("should reject duplicate category name for same user")
        void rejectDuplicateNameForSameUser() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createCategory(new CreateCategoryDto("Groceries", null), ownerUser)
            );
            assertEquals(409, ex.getStatusCode().value());
            assertNotNull(ex.getReason());
            assertTrue(ex.getReason().contains("already exists"));
        }

        @Test
        @DisplayName("should reject duplicate category name case-insensitively")
        void rejectDuplicateNameCaseInsensitive() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "groceries")).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createCategory(new CreateCategoryDto("groceries", null), ownerUser)
            );
            assertEquals(409, ex.getStatusCode().value());
            assertNotNull(ex.getReason());
            assertTrue(ex.getReason().contains("already exists"));
        }

        @Test
        @DisplayName("should allow same category name for different users")
        void allowSameNameForDifferentUsers() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false);
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(2L, "Groceries")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> {
                CategoryTag tag = invocation.getArgument(0);
                tag.setId(UUID.randomUUID());
                return tag;
            });

            var result1 = service.createCategory(new CreateCategoryDto("Groceries", null), ownerUser);
            var result2 = service.createCategory(new CreateCategoryDto("Groceries", null), otherUser);
            assertEquals("Groceries", result1.getName());
            assertEquals("Groceries", result2.getName());
        }

        @Test
        @DisplayName("should trim whitespace from category name")
        void trimWhitespaceFromName() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Groceries")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> {
                CategoryTag tag = invocation.getArgument(0);
                tag.setId(UUID.randomUUID());
                return tag;
            });

            var result = service.createCategory(new CreateCategoryDto("  Groceries  ", null), ownerUser);
            assertEquals("Groceries", result.getName());
        }

        @Test
        @DisplayName("should set owner to the authenticated user")
        void setOwnerToAuthenticatedUser() {
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> {
                CategoryTag tag = invocation.getArgument(0);
                assertNotNull(tag.getOwner());
                assertEquals(ownerUser.getUserId(), tag.getOwner().getUserId());
                tag.setId(UUID.randomUUID());
                return tag;
            });

            service.createCategory(new CreateCategoryDto("Food", null), ownerUser);
            verify(categoryTagRepository).save(any(CategoryTag.class));
        }
    }

    @Nested
    @DisplayName("listCategories")
    class ListCategories {

        @Test
        @DisplayName("should return all categories for owner")
        void returnAllCategoriesForOwner() {
            List<CategoryTag> tags = List.of(
                new CategoryTag(UUID.randomUUID(), ownerUser, "Groceries", null, Instant.now()),
                new CategoryTag(UUID.randomUUID(), ownerUser, "Electricity", null, Instant.now())
            );
            when(categoryTagRepository.findByOwnerUserId(1L)).thenReturn(tags);

            var result = service.listCategories(ownerUser);
            assertEquals(2, result.size());
            assertEquals("Groceries", result.get(0).getName());
            assertEquals("Electricity", result.get(1).getName());
        }

        @Test
        @DisplayName("should return empty list when user has no categories")
        void returnEmptyListWhenNoCategories() {
            when(categoryTagRepository.findByOwnerUserId(1L)).thenReturn(List.of());

            var result = service.listCategories(ownerUser);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategory {

        private final UUID categoryId = UUID.randomUUID();
        private CategoryTag existingTag;

        @BeforeEach
        void setUp() {
            existingTag = new CategoryTag(categoryId, ownerUser, "Groceries", null, Instant.now());
        }

        @Test
        @DisplayName("should rename category")
        void renameCategory() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = service.updateCategory(categoryId, new UpdateCategoryDto("Food", null), ownerUser);
            assertEquals("Food", result.getName());
        }

        @Test
        @DisplayName("should reject rename to existing name for same user")
        void rejectRenameToDuplicateName() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Electricity")).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateCategory(categoryId, new UpdateCategoryDto("Electricity", null), ownerUser)
            );
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("should reject rename to existing name case-insensitively")
        void rejectRenameToDuplicateNameCaseInsensitive() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "electricity")).thenReturn(true);

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateCategory(categoryId, new UpdateCategoryDto("electricity", null), ownerUser)
            );
            assertEquals(409, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("should allow renaming to the same name (no-op)")
        void allowRenamingToSameName() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = service.updateCategory(categoryId, new UpdateCategoryDto("Groceries", null), ownerUser);
            assertEquals("Groceries", result.getName());
        }

        @Test
        @DisplayName("should reject update by non-owner")
        void rejectUpdateByNonOwner() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));

            assertThrows(
                ForbiddenException.class,
                () -> service.updateCategory(categoryId, new UpdateCategoryDto("Stolen", null), otherUser)
            );
        }

        @Test
        @DisplayName("should return 404 for non-existent category")
        void notFoundForMissingCategory() {
            UUID missingId = UUID.randomUUID();
            when(categoryTagRepository.findById(missingId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateCategory(missingId, new UpdateCategoryDto("X", null), ownerUser)
            );
            assertEquals(404, ex.getStatusCode().value());
        }

        @Test
        @DisplayName("should trim whitespace when renaming")
        void trimWhitespaceOnRename() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));
            when(categoryTagRepository.existsByOwnerUserIdAndNameIgnoreCase(1L, "Food")).thenReturn(false);
            when(categoryTagRepository.save(any(CategoryTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = service.updateCategory(categoryId, new UpdateCategoryDto("  Food  ", null), ownerUser);
            assertEquals("Food", result.getName());
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategory {

        private final UUID categoryId = UUID.randomUUID();
        private CategoryTag existingTag;

        @BeforeEach
        void setUp() {
            existingTag = new CategoryTag(categoryId, ownerUser, "Groceries", null, Instant.now());
        }

        @Test
        @DisplayName("should delete category owned by user")
        void deleteCategoryOwnedByUser() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));

            service.deleteCategory(categoryId, ownerUser);
            verify(categoryTagRepository).delete(existingTag);
        }

        @Test
        @DisplayName("should reject delete by non-owner")
        void rejectDeleteByNonOwner() {
            when(categoryTagRepository.findById(categoryId)).thenReturn(Optional.of(existingTag));

            assertThrows(ForbiddenException.class, () -> service.deleteCategory(categoryId, otherUser));
            verify(categoryTagRepository, never()).delete(any(CategoryTag.class));
        }

        @Test
        @DisplayName("should return 404 for non-existent category")
        void notFoundForMissingCategory() {
            UUID missingId = UUID.randomUUID();
            when(categoryTagRepository.findById(missingId)).thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.deleteCategory(missingId, ownerUser)
            );
            assertEquals(404, ex.getStatusCode().value());
        }
    }

    private static User createUser(long userId, String email) {
        User user = new User();
        user.setUserId(userId);
        user.setPassword("encoded");
        user.setEmail(email);
        return user;
    }
}
