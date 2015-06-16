package com.dr8.xposedmtc.utils;

import java.io.Serializable;

public final class Cover implements Serializable {

    private static final long serialVersionUID = 1L;

    public final String name;

    public final String image;

    public Cover(String name, String image)
    {
        this.name = name;
        this.image = image;
    }

}
