package dev.civcore.sim.ruleset;

import com.google.gson.JsonParser;
import dev.civcore.sim.TestRulesets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesetTest {

    @Test
    void shippedAncientRulesetLoadsAndValidates() {
        RulesetLoader loader = TestRulesets.shippedLoader();
        assertEquals(List.of(), loader.errors());
        Ruleset r = loader.build();
        assertEquals(12, r.techs().size());
        assertEquals(8, r.buildings().size());
        assertEquals(6, r.units().size());
        assertEquals(List.of(), RulesetValidator.validate(r));
        assertEquals(List.of("civcore:granary"), r.techs().get("civcore:pottery").unlocks().buildings());
    }

    @Test
    void hashIsStableAndContentSensitive() {
        Ruleset a = TestRulesets.shipped();
        Ruleset b = TestRulesets.shipped();
        assertEquals(a.hash(), b.hash());
        Ruleset c = new RulesetLoader()
            .add("techs", "x.json", JsonParser.parseString("{\"id\":\"t:x\",\"cost\":5}"))
            .build();
        assertNotEquals(a.hash(), c.hash());
    }

    @Test
    void validatorReportsMissingReferencesAndCycles() {
        Ruleset r = new RulesetLoader()
            .add("techs", "a.json", JsonParser.parseString("{\"id\":\"t:a\",\"cost\":5,\"requires\":[\"t:b\"]}"))
            .add("techs", "b.json", JsonParser.parseString("{\"id\":\"t:b\",\"cost\":5,\"requires\":[\"t:a\"],\"unlocks\":{\"buildings\":[\"b:nope\"]}}"))
            .add("units", "u.json", JsonParser.parseString("{\"id\":\"u:x\",\"cost\":0,\"requiresTech\":\"t:zzz\"}"))
            .build();
        List<String> errors = RulesetValidator.validate(r);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown building b:nope")), errors::toString);
        assertTrue(errors.stream().anyMatch(e -> e.contains("unknown tech t:zzz")), errors::toString);
        assertTrue(errors.stream().anyMatch(e -> e.contains("cost must be positive")), errors::toString);
        assertTrue(errors.stream().anyMatch(e -> e.contains("cycle")), errors::toString);
    }

    @Test
    void loaderReportsDuplicateAndMissingIds() {
        RulesetLoader l = new RulesetLoader()
            .add("techs", "a.json", JsonParser.parseString("{\"id\":\"t:a\",\"cost\":5}"))
            .add("techs", "a2.json", JsonParser.parseString("{\"id\":\"t:a\",\"cost\":5}"))
            .add("units", "noid.json", JsonParser.parseString("{\"cost\":5}"));
        assertEquals(2, l.errors().size(), l.errors()::toString);
    }
}
