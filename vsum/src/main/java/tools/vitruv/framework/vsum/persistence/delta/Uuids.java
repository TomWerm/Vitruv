package tools.vitruv.framework.vsum.persistence.delta;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import tools.vitruv.change.atomic.uuid.Uuid;

/**
 * Converts between {@link Uuid} and its raw string. {@code Uuid(String)} and {@code getRawValue()}
 * are package-private in Vitruv-Change (4.0.0 and later), so this goes through reflection. Drop it
 * once Vitruv-Change offers a public factory and getter.
 */
final class Uuids {
  private static final Constructor<Uuid> CONSTRUCTOR;
  private static final Method RAW_VALUE;

  static {
    try {
      CONSTRUCTOR = Uuid.class.getDeclaredConstructor(String.class);
      CONSTRUCTOR.setAccessible(true);
      RAW_VALUE = Uuid.class.getDeclaredMethod("getRawValue");
      RAW_VALUE.setAccessible(true);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private Uuids() {}

  /**
   * Returns the UUID with the given raw value.
   *
   * @param raw the raw value
   * @return the UUID
   */
  static Uuid of(String raw) {
    try {
      return CONSTRUCTOR.newInstance(raw);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns the raw value of a UUID.
   *
   * @param uuid the UUID
   * @return its raw value
   */
  static String raw(Uuid uuid) {
    try {
      return (String) RAW_VALUE.invoke(uuid);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }
}
