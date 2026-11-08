package dev.civcore.sim;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** civ-sim must stay free of Minecraft, NeoForge and mod types (docs/J-implementation-guide.md §0). */
@AnalyzeClasses(packages = "dev.civcore.sim", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule simIsPureJava = noClasses().that().resideInAPackage("dev.civcore.sim..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "net.minecraft..", "net.neoforged..", "com.mojang..", "com.minecolonies..", "com.ldtteam..",
            "com.talhanation..", "xaero..", "com.exoticworlds..");
}
