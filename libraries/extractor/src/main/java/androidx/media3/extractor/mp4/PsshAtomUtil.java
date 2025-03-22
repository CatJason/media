package androidx.media3.extractor.mp4;

import androidx.annotation.Nullable;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.Mp4Box;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Utility methods for handling PSSH atoms.
 */
@UnstableApi
public final class PsshAtomUtil {

  private static final String TAG = "PsshAtomUtil";

  private PsshAtomUtil() {
  }

  /**
   * 为给定的系统 ID 构建一个版本 0 的 PSSH 原子，包含给定的数据。
   *
   * @param systemId 加密系统的 ID。
   * @param data     加密方案特定的数据。
   * @return PSSH 原子。
   */
  public static byte[] buildPsshAtom(UUID systemId, @Nullable byte[] data) {
    return buildPsshAtom(systemId, null, data);
  }

  /**
   * 为给定的系统 ID 构建一个 PSSH 原子，包含给定的密钥 ID 和数据。
   *
   * @param systemId 加密系统的 ID。
   * @param keyIds   用于版本 1 PSSH 原子的密钥 ID，或 null 用于版本 0 PSSH 原子。
   * @param data     加密方案特定的数据。
   * @return PSSH 原子。
   */
  public static byte[] buildPsshAtom(
      UUID systemId, @Nullable UUID[] keyIds, @Nullable byte[] data) {
    int dataLength = data != null ? data.length : 0;
    int psshBoxLength = Mp4Box.FULL_HEADER_SIZE + 16 /* SystemId */ + 4 /* DataSize */ + dataLength;
    if (keyIds != null) {
      psshBoxLength += 4 /* KID_count */ + (keyIds.length * 16) /* KIDs */;
    }
    ByteBuffer psshBox = ByteBuffer.allocate(psshBoxLength);
    psshBox.putInt(psshBoxLength);
    psshBox.putInt(Mp4Box.TYPE_pssh);
    psshBox.putInt(keyIds != null ? 0x01000000 : 0 /* version=(buildV1Atom ? 1 : 0), flags=0 */);
    psshBox.putLong(systemId.getMostSignificantBits());
    psshBox.putLong(systemId.getLeastSignificantBits());
    if (keyIds != null) {
      psshBox.putInt(keyIds.length);
      for (UUID keyId : keyIds) {
        psshBox.putLong(keyId.getMostSignificantBits());
        psshBox.putLong(keyId.getLeastSignificantBits());
      }
    }
    if (data != null && data.length != 0) {
      psshBox.putInt(data.length);
      psshBox.put(data);
    } else {
      psshBox.putInt(0);
    }
    return psshBox.array();
  }

  /**
   * 返回数据是否为有效的 PSSH 原子。
   *
   * @param data 要解析的数据。
   * @return 数据是否为有效的 PSSH 原子。
   */
  public static boolean isPsshAtom(byte[] data) {
    return parsePsshAtom(data) != null;
  }

  /**
   * 从 PSSH 原子中解析 UUID。支持版本 0 和 1 的 PSSH 原子。
   *
   * <p>仅当数据是有效的 PSSH 原子时，才会解析 UUID。
   *
   * @param atom 要解析的原子。
   * @return 解析出的 UUID。如果输入不是有效的 PSSH 原子，或 PSSH 原子版本不受支持，则返回 null。
   */
  @Nullable
  public static UUID parseUuid(byte[] atom) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    return parsedAtom.uuid;
  }

  /**
   * 从 PSSH 原子中解析版本。支持版本 0 和 1 的 PSSH 原子。
   *
   * <p>仅当数据是有效的 PSSH 原子时，才会解析版本。
   *
   * @param atom 要解析的原子。
   * @return 解析出的版本。如果输入不是有效的 PSSH 原子，或 PSSH 原子版本不受支持，则返回 -1。
   */
  public static int parseVersion(byte[] atom) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return -1;
    }
    return parsedAtom.version;
  }

  /**
   * 从 PSSH 原子中解析加密方案特定的数据。支持版本 0 和 1 的 PSSH 原子。
   *
   * <p>仅当数据是有效的 PSSH 原子且与给定的 UUID 匹配时，才会解析数据。
   * 如果传入的 UUID 为 null，则接受任何类型的有效 PSSH 原子。
   *
   * @param atom 要解析的原子。
   * @param uuid 所需的 PSSH 原子的 UUID，或 null 以接受任何 UUID。
   * @return 解析出的加密方案特定的数据。如果输入不是有效的 PSSH 原子，或 PSSH 原子版本不受支持，
   * 或 PSSH 原子与传入的 UUID 不匹配，则返回 null。
   */
  @Nullable
  public static byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
    @Nullable PsshAtom parsedAtom = parsePsshAtom(atom);
    if (parsedAtom == null) {
      return null;
    }
    if (!uuid.equals(parsedAtom.uuid)) {
      Log.w(TAG, "UUID mismatch. Expected: " + uuid + ", got: " + parsedAtom.uuid + ".");
      return null;
    }
    return parsedAtom.schemeData;
  }

  /**
   * 解析 PSSH 原子。支持版本 0 和 1 的 PSSH 原子。
   *
   * @param atom 要解析的原子。
   * @return 解析出的 PSSH 原子。如果输入不是有效的 PSSH 原子，或 PSSH 原子版本不受支持，则返回 null。
   */
  @Nullable
  public static PsshAtom parsePsshAtom(byte[] atom) {
    ParsableByteArray atomData = new ParsableByteArray(atom);
    // 数据太短。
    if (atomData.limit() < Mp4Box.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */) {
      // Data too short.
      return null;
    }
    atomData.setPosition(0);
    int bufferLength = atomData.bytesLeft();
    int atomSize = atomData.readInt();
    if (atomSize != bufferLength) {
      // 声明的原子大小 (X) 与缓冲区大小不匹配。
      Log.w(
          TAG,
          "Advertised atom size (" + atomSize + ") does not match buffer size: " + bufferLength);
      return null;
    }
    int atomType = atomData.readInt();
    if (atomType != Mp4Box.TYPE_pssh) {
      // 原子类型不是 pssh: X
      Log.w(TAG, "Atom type is not pssh: " + atomType);
      return null;
    }
    int atomVersion = BoxParser.parseFullBoxVersion(atomData.readInt());
    if (atomVersion > 1) {
      // 不支持的 pssh 版本: X
      Log.w(TAG, "Unsupported pssh version: " + atomVersion);
      return null;
    }
    UUID uuid = new UUID(atomData.readLong(), atomData.readLong());
    UUID[] keyIds = null;
    if (atomVersion == 1) {
      int keyIdCount = atomData.readUnsignedIntToInt();
      keyIds = new UUID[keyIdCount];
      for (int i = 0; i < keyIdCount; ++i) {
        keyIds[i] = new UUID(atomData.readLong(), atomData.readLong());
      }
    }
    int dataSize = atomData.readUnsignedIntToInt();
    bufferLength = atomData.bytesLeft();
    if (dataSize != bufferLength) {
      // 原子数据大小 (X) 与剩余字节数不匹配。
      Log.w(
          TAG, "Atom data size (" + dataSize + ") does not match the bytes left: " + bufferLength);
      return null;
    }
    byte[] data = new byte[dataSize];
    atomData.readBytes(data, 0, dataSize);
    return new PsshAtom(uuid, atomVersion, data, keyIds);
  }

  /**
   * 表示 mp4 PSSH 原子的类，如 ISO/IEC 23001-7 中所定义。
   */
  public static final class PsshAtom {

    /**
     * 加密系统的 UUID，如 ISO/IEC 23009-1 第 5.8.4.1 节中所定义。
     */
    public final UUID uuid;

    /**
     * PSSH 原子的版本，为 0 或 1。
     */
    public final int version;

    /**
     * 二进制加密方案数据。
     */
    public final byte[] schemeData;

    /**
     * 密钥 ID 数组。对于版本 0 始终为 null，对于版本 1 始终为非 null。
     */
    @Nullable
    public final UUID[] keyIds;

    /* package */ PsshAtom(UUID uuid, int version, byte[] schemeData, @Nullable UUID[] keyIds) {
      this.uuid = uuid;
      this.version = version;
      this.schemeData = schemeData;
      this.keyIds = keyIds;
    }
  }
}
