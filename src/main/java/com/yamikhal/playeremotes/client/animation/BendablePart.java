package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.anim.Bend;
import org.jetbrains.annotations.Nullable;

// added to model parts: bend a limb is drawn with (see ModelPartMixin), outer layer shares its limb's bend
public interface BendablePart {

    @Nullable
    Bend playeremotes$bend();

    void playeremotes$setBend(@Nullable Bend bend);
}
