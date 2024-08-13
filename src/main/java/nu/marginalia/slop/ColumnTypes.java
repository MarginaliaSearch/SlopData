package nu.marginalia.slop;

import nu.marginalia.slop.column.ColumnReader;
import nu.marginalia.slop.column.ColumnWriter;
import nu.marginalia.slop.column.array.*;
import nu.marginalia.slop.column.dynamic.*;
import nu.marginalia.slop.column.primitive.*;
import nu.marginalia.slop.column.string.*;
import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnType;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ColumnTypes {
    private static Map<String, ColumnType<? extends ColumnReader,? extends ColumnWriter>> byMnemonic = new HashMap<>();

    public static ColumnType<ByteColumnReader, ByteColumnWriter> BYTE = register("s8", ByteOrder.nativeOrder(), ByteColumn::open, ByteColumn::create);
    public static ColumnType<CharColumnReader, CharColumnWriter> CHAR_LE = register("u16le", ByteOrder.LITTLE_ENDIAN, CharColumn::open, CharColumn::create);
    public static ColumnType<CharColumnReader, CharColumnWriter> CHAR_BE = register("u16be", ByteOrder.BIG_ENDIAN, CharColumn::open, CharColumn::create);
    public static ColumnType<ShortColumnReader, ShortColumnWriter> SHORT_LE = register("s16le", ByteOrder.LITTLE_ENDIAN, ShortColumn::open, ShortColumn::create);
    public static ColumnType<ShortColumnReader, ShortColumnWriter> SHORT_BE = register("s16be", ByteOrder.BIG_ENDIAN, ShortColumn::open, ShortColumn::create);
    public static ColumnType<IntColumnReader, IntColumnWriter> INT_LE = register("s32le", ByteOrder.LITTLE_ENDIAN, IntColumn::open, IntColumn::create);
    public static ColumnType<IntColumnReader, IntColumnWriter> INT_BE = register("s32be", ByteOrder.BIG_ENDIAN, IntColumn::open, IntColumn::create);
    public static ColumnType<LongColumnReader, LongColumnWriter> LONG_LE = register("s64le", ByteOrder.LITTLE_ENDIAN, LongColumn::open, LongColumn::create);
    public static ColumnType<LongColumnReader, LongColumnWriter> LONG_BE = register("s64be", ByteOrder.BIG_ENDIAN, LongColumn::open, LongColumn::create);
    public static ColumnType<FloatColumnReader, FloatColumnWriter> FLOAT_LE = register("fp32le", ByteOrder.LITTLE_ENDIAN, FloatColumn::open, FloatColumn::create);
    public static ColumnType<FloatColumnReader, FloatColumnWriter> FLOAT_BE = register("fp32be", ByteOrder.BIG_ENDIAN, FloatColumn::open, FloatColumn::create);
    public static ColumnType<DoubleColumnReader, DoubleColumnWriter> DOUBLE_LE = register("fp64le", ByteOrder.LITTLE_ENDIAN, DoubleColumn::open, DoubleColumn::create);
    public static ColumnType<DoubleColumnReader, DoubleColumnWriter> DOUBLE_BE = register("fp64be", ByteOrder.BIG_ENDIAN, DoubleColumn::open, DoubleColumn::create);
    public static ColumnType<VarintColumnReader, VarintColumnWriter> VARINT_LE = register("varintle", ByteOrder.LITTLE_ENDIAN, VarintColumn::open, VarintColumn::create);
    public static ColumnType<VarintColumnReader, VarintColumnWriter> VARINT_BE = register("varintbe", ByteOrder.BIG_ENDIAN, VarintColumn::open, VarintColumn::create);
    public static ColumnType<CustomBinaryColumnReader, CustomBinaryColumnWriter> BYTE_ARRAY_CUSTOM = register("s8[]+custom", ByteOrder.nativeOrder(), CustomBinaryColumn::open, CustomBinaryColumn::create);

    public static ColumnType<StringColumnReader, StringColumnWriter> STRING = register("s8[]+str", ByteOrder.nativeOrder(), StringColumn::open, StringColumn::create);
    public static ColumnType<StringColumnReader, StringColumnWriter> CSTRING = register("s8+cstr", ByteOrder.nativeOrder(), StringColumn::open, StringColumn::create);
    public static ColumnType<StringColumnReader, StringColumnWriter> TXTSTRING = register("s8+txt", ByteOrder.nativeOrder(), StringColumn::open, StringColumn::create);


    public static ColumnType<EnumColumnReader, StringColumnWriter> ENUM_8 = register("u8+enum", ByteOrder.LITTLE_ENDIAN, EnumColumn::open8, EnumColumn::create8);
    public static ColumnType<EnumColumnReader, StringColumnWriter> ENUM_LE = register("varintle+enum", ByteOrder.LITTLE_ENDIAN, EnumColumn::open, EnumColumn::create);
    public static ColumnType<EnumColumnReader, StringColumnWriter> ENUM_BE = register("varintbe+enum", ByteOrder.BIG_ENDIAN, EnumColumn::open, EnumColumn::create);

    public static ColumnType<ByteArrayColumnReader, ByteArrayColumnWriter> BYTE_ARRAY = register("s8[]", ByteOrder.nativeOrder(), ByteArrayColumn::open, ByteArrayColumn::create);
    public static ColumnType<ObjectArrayColumnReader<byte[]>, ObjectArrayColumnWriter<byte[]>> BYTE_ARRAY_ARRAY = register("s8[][]", ByteOrder.nativeOrder(), ByteArrayColumn::openNested, ByteArrayColumn::createNested);
    public static ColumnType<LongArrayColumnReader, LongArrayColumnWriter> LONG_ARRAY_LE = register("s64le[]", ByteOrder.LITTLE_ENDIAN, LongArrayColumn::open, LongArrayColumn::create);
    public static ColumnType<LongArrayColumnReader, LongArrayColumnWriter> LONG_ARRAY_BE = register("s64be[]", ByteOrder.BIG_ENDIAN, LongArrayColumn::open, LongArrayColumn::create);

    public static ColumnType<ObjectArrayColumnReader<String>, ObjectArrayColumnWriter<String>> STRING_ARRAY = register("s8[]+str[]", ByteOrder.nativeOrder(), StringColumn::openArray, StringColumn::createArray);
    public static ColumnType<ObjectArrayColumnReader<String>, ObjectArrayColumnWriter<String>> CSTRING_ARRAY = register("s8+cstr[]", ByteOrder.nativeOrder(), StringColumn::openArray, StringColumn::createArray);
    public static ColumnType<ObjectArrayColumnReader<String>, ObjectArrayColumnWriter<String>> TXTSTRING_ARRAY = register("s8+txt", ByteOrder.nativeOrder(), StringColumn::openArray, StringColumn::createArray);

    public static ColumnType<IntArrayColumnReader, IntArrayColumnWriter> INT_ARRAY_LE = register("s32le[]", ByteOrder.LITTLE_ENDIAN, IntArrayColumn::open, IntArrayColumn::create);
    public static ColumnType<IntArrayColumnReader, IntArrayColumnWriter> INT_ARRAY_BE = register("s32be[]", ByteOrder.BIG_ENDIAN, IntArrayColumn::open, IntArrayColumn::create);
    public static ColumnType<ObjectArrayColumnReader<int[]>, ObjectArrayColumnWriter<int[]>> INT_ARRAY_ARRAY_LE = register("s32le[][]", ByteOrder.LITTLE_ENDIAN, IntArrayColumn::openNested, IntArrayColumn::createNested);
    public static ColumnType<ObjectArrayColumnReader<int[]>, ObjectArrayColumnWriter<int[]>> INT_ARRAY_ARRAY_BE = register("s32be[][]", ByteOrder.BIG_ENDIAN, IntArrayColumn::openNested, IntArrayColumn::createNested);
    public static ColumnType<ObjectArrayColumnReader<long[]>, ObjectArrayColumnWriter<long[]>> LONG_ARRAY_ARRAY_LE = register("s64le[][]", ByteOrder.LITTLE_ENDIAN, LongArrayColumn::openNested, LongArrayColumn::createNested);
    public static ColumnType<ObjectArrayColumnReader<long[]>, ObjectArrayColumnWriter<long[]>> LONG_ARRAY_ARRAY_BE = register("s64be[][]", ByteOrder.BIG_ENDIAN, LongArrayColumn::openNested, LongArrayColumn::createNested);

    public static ColumnType<? extends ColumnReader,? extends ColumnWriter> byMnemonic(String mnemonic) {
        return byMnemonic.get(mnemonic);
    }

    public interface ColumnOpener<T extends ColumnReader> {
        T open(Path path, ColumnDesc desc) throws IOException;
    }
    public interface ColumnCreator<T extends ColumnWriter> {
        T create(Path path, ColumnDesc desc) throws IOException;
    }

    /** Register a new type
     *
     * @param mnemonic - the type identifier, part of the filename, must be unique
     * @param byteOrder - the byte order of the type
     * @param readerCons - reader constructor
     * @param writerCons - reader constructor
     * */
    public static <R extends ColumnReader, W extends ColumnWriter, T extends ColumnType<R,W>> ColumnType<R, W>
    register(String mnemonic, ByteOrder byteOrder, ColumnOpener<R> readerCons, ColumnCreator<W> writerCons)
    {

        var ins = new ColumnType<R, W>() {
            @Override
            public String mnemonic() {
                return mnemonic;
            }

            public ByteOrder byteOrder() {
                return byteOrder;
            }

            @Override
            public R open(Path path, ColumnDesc<R, W> desc) throws IOException {
                return readerCons.open(path, desc);
            }

            @Override
            public W create(Path path, ColumnDesc<R, W> desc) throws IOException {
                return writerCons.create(path, desc);
            }
        };

        byMnemonic.put(mnemonic, ins);

        return ins;
    }

}
