package com.tygabeast.arssourcequarry.compat.jade;

import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlock;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Optional Jade support. Jade finds this class through its annotation and only when Jade is installed;
 * nothing else in the mod refers to it, so the mod runs normally without Jade.
 */
@WailaPlugin(ArsSourceQuarry.MODID)
public class QuarryJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(QuarryJadeProvider.INSTANCE, SourceQuarryBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(QuarryJadeProvider.INSTANCE, SourceQuarryBlock.class);
    }
}
