package androidx.media3.common.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;

/**
 * Annotation for specifying a nullable type.
 *
 * <p>Unlike {@link androidx.annotation.Nullable} used elsewhere in the library, this annotation can
 * be used on {@link ElementType#TYPE_USE} locations like generic type parameters and array element
 * types.
 */
@UnstableApi
@Documented
@Retention(RetentionPolicy.CLASS)
@Nonnull(when = When.MAYBE)
@Target({ElementType.TYPE_USE})
@TypeQualifierNickname
public @interface NullableType {}
