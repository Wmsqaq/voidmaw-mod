package com.novapupil.voidmaw;

import net.fabricmc.api.ModInitializer;

public final class RegressionEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("voidmaw.regression")) {
            return;
        }
        try {
            com.novapupil.voidmaw.blackhole.AbsorptionRegressionTest.main(new String[0]);
            com.novapupil.voidmaw.warehouse.WarehouseRegressionTest.main(new String[0]);
            com.novapupil.voidmaw.item.CoreLockRegressionTest.main(new String[0]);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}
