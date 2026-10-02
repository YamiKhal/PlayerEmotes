package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.anim.Bend;
import org.jetbrains.annotations.Nullable;

// added to model parts: the bend a limb is drawn with (see ModelPartMixin). a limb's outer layer shares its bend
public interface BendablePart {

    @Nullable
    Bend playeremotes$bend();

    void playeremotes$setBend(@Nullable Bend bend);
}
