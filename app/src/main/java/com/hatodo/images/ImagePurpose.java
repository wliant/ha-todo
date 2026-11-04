package com.hatodo.images;

/**
 * What an image is for, which is also what decides who may read it.
 *
 * <p>An {@link #AVATAR} is readable by anyone, including anonymously - the
 * sign-in screen draws it before there is a session (adr/0019). A
 * {@link #DETAIL} picture inherits the access rule of the list it belongs to.
 * That asymmetry is load-bearing, so it is a column rather than a convention.
 */
public enum ImagePurpose {
    AVATAR,
    DETAIL
}
