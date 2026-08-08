package com.canmakan.backend.family;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.dietaryprofile.DietaryProfileService;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyExceptionHandler;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.model.CreateFamilyRequest;
import com.canmakan.backend.family.model.FamilyMeResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** UC8: FamilyController HTTP contract tests
 * 
 * @author Amelia
 */
@DisplayName("UC8: FamilyController HTTP contract tests")
class FamilyControllerTest {

        private MockMvc mockMvc;
        private FamilyService familyService;

        // UC8 setup mock mvc and validator
        @BeforeEach
        void setUp() {
                familyService = mock(FamilyService.class);
                DietaryProfileService dietaryProfileService = mock(DietaryProfileService.class);
                FamilyController controller = new FamilyController(dietaryProfileService, familyService);
                LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
                validator.afterPropertiesSet();
                mockMvc = MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new FamilyExceptionHandler())
                        .setValidator(validator)
                        .build();
        }

        // UC8 create circle returns 201
        @Test
        @DisplayName("POST /api/families returns 201")
        void createReturns201() throws Exception {
                when(familyService.createFamily(eq(14L), any(CreateFamilyRequest.class)))
                        .thenReturn(new FamilyMeResponse(50L, "Wong Family", "PRIMARY_ADMIN", 77L, 14L));

                mockMvc.perform(post("/api/families")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"familyName\":\"Wong Family\"}")
                                .header("X-User-Id", "14"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.familyId").value(50))
                        .andExpect(jsonPath("$.familyName").value("Wong Family"))
                        .andExpect(jsonPath("$.memberRole").value("PRIMARY_ADMIN"))
                        .andExpect(jsonPath("$.selfProfileId").value(77));
        }

        // UC8 create circle blank name returns 400 via @Valid
        @Test
        @DisplayName("POST /api/families blank name returns 400 via @Valid")
        void createBlankName() throws Exception {
                mockMvc.perform(post("/api/families")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"familyName\":\"  \"}")
                                .header("X-User-Id", "14"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message").value("Family name is required."));

                verify(familyService, never()).createFamily(any(Long.class), any(CreateFamilyRequest.class));
        }

        // UC8 create circle second create returns 409
        @Test
        @DisplayName("POST /api/families second create returns 409")
        void createConflict() throws Exception {
                when(familyService.createFamily(eq(4L), any(CreateFamilyRequest.class)))
                        .thenThrow(new AlreadyInFamilyException("You already belong to a family circle."));

                mockMvc.perform(post("/api/families")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"familyName\":\"Second\"}")
                                .header("X-User-Id", "4"))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.message").value("You already belong to a family circle."));
        }

        // UC8 get circle by user id returns 200
        @Test
        @DisplayName("GET /api/families/me returns 200")
        void getMeOk() throws Exception {
                when(familyService.getMyFamily(4L))
                        .thenReturn(new FamilyMeResponse(1L, "Tan Family", "PRIMARY_ADMIN", 1L, 4L));

                mockMvc.perform(get("/api/families/me").header("X-User-Id", "4"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.familyId").value(1))
                        .andExpect(jsonPath("$.familyName").value("Tan Family"));
        }

        // UC8 get circle by user id without membership returns 404
        @Test
        @DisplayName("GET /api/families/me without membership returns 404")
        void getMeNotFound() throws Exception {
                when(familyService.getMyFamily(99L))
                        .thenThrow(new FamilyNotFoundException("You are not a member of a family circle."));

                mockMvc.perform(get("/api/families/me").header("X-User-Id", "99"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("You are not a member of a family circle."));
        }
}
