package com.tygabeast.arssourcequarry.client;

import com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent;
import com.hollingsworth.arsnouveau.api.documentation.builder.DocEntryBuilder;
import com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry;
import com.hollingsworth.arsnouveau.setup.registry.Documentation;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only registration: the screen and the worn notebook pages. */
public final class ClientSetup {
    private ClientSetup() {}

    @EventBusSubscriber(modid = ArsSourceQuarry.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ArsSourceQuarry.QUARRY_MENU.get(), SourceQuarryScreen::new);
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(ArsSourceQuarry.MARKER_BE.get(), MarkerRenderer::new);
        }
    }

    @EventBusSubscriber(modid = ArsSourceQuarry.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        /** Ars Nouveau rebuilds its notebook on every world join; this adds our pages each time. */
        @SubscribeEvent
        public static void addDocs(ReloadDocumentationEvent.AddEntries event) {
            String ns = ArsSourceQuarry.MODID;
            Documentation.addPage(new DocEntryBuilder(ns, DocumentationRegistry.CRAFTING, ArsSourceQuarry.SOURCE_QUARRY_ITEM.get())
                    .withIntroPage()
                    .withTextPage(ns + ".page.source_quarry.area")
                    .withTextPage(ns + ".page.source_quarry.glyphs")
                    .withTextPage(ns + ".page.source_quarry.output")
                    .withCraftingPages(ArsSourceQuarry.SOURCE_QUARRY_ITEM.get()));
            Documentation.addPage(new DocEntryBuilder(ns, DocumentationRegistry.CRAFTING, ArsSourceQuarry.LEY_MARKER_ITEM.get())
                    .withIntroPage()
                    .withCraftingPages(ArsSourceQuarry.LEY_MARKER_ITEM.get()));
        }
    }
}
