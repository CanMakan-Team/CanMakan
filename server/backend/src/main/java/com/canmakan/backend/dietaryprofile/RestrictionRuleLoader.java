package com.canmakan.backend.dietaryprofile;

import com.canmakan.backend.product.verdict.RestrictionRule;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges the "update dietary preference" data into the scan flow: loads a
 * profile's saved {@code profile_restrictions} (joined to {@code dietary_restrictions})
 * and flattens them into the {@link RestrictionRule} list the
 * {@code DietaryRuleEngine} consumes.
 *
 * @author XieHuayuan
 */
@Service
public class RestrictionRuleLoader {

    private final DietaryProfileRepository dietaryProfileRepository;

    public RestrictionRuleLoader(DietaryProfileRepository dietaryProfileRepository) {
        this.dietaryProfileRepository = dietaryProfileRepository;
    }

    /**
     * Load the active restriction rules for a dietary profile.
     *
     * @param profileId the profile the product is assessed against
     * @return the flattened rules (code + category + severity)
     */
    public List<RestrictionRule> load(Long profileId) {
        // TODO: read profile_restrictions for profileId, map severity_level ->
        //       RestrictionSeverity, category -> RestrictionCategory, build RestrictionRule.
        throw new UnsupportedOperationException("TODO: implement");
    }
}
