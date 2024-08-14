package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.StorageType;

import java.nio.ByteOrder;

public abstract class AbstractObjectColumn<T, R extends ObjectColumnReader<T>, W extends ObjectColumnWriter<T>>
 extends AbstractColumn<R, W> {

    public AbstractObjectColumn(String name,
                                String typeMnemonic,
                                ByteOrder byteOrder,
                                ColumnFunction function,
                                StorageType storageType)
    {
        super(name, typeMnemonic, byteOrder, function, storageType);
    }


}
