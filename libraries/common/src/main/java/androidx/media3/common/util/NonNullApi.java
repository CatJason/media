package androidx.media3.common.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierDefault;
import kotlin.annotations.jvm.MigrationStatus;
import kotlin.annotations.jvm.UnderMigration;

/**
 * Annotation to declare all type usages in the annotated instance as {@link Nonnull}, unless
 * explicitly marked with a nullable annotation.
 */
// MigrationStatus.STRICT is marked as deprecated because it's considered experimental
@SuppressWarnings("deprecation")
@Nonnull
@TypeQualifierDefault(ElementType.TYPE_USE)
@UnderMigration(status = MigrationStatus.STRICT)
@Retention(RetentionPolicy.CLASS)
@UnstableApi
public @interface NonNullApi {}
