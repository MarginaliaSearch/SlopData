# Slop

Slop is a Java 22+ library for columnar data persistence.  It will also 
build on Java 21 with the `--enable-preview` flag.  

**The library is in an early stage of development.**

It is designed to be used for storing large amounts of data in a way 
that is both fast and memory-efficient.  The data is write-once, and 
the Slop library offers many facilities for deciding how it should be 
stored and accessed.   It does not replace a DBMS, but is a storage
format for data at rest. 

Slop was put together because Parquet support on Java outside the Hadoop 
ecosystem is a pain in the posterior.

It is designed to be used in the Marginalia Search engine for storing
intermediate representations of crawled documents, and batch processing
the same data, but can be used elsewhere where is need for storing very 
large amounts of data.  

Modern drives can read and write data at ~ 500 MB/s and RAM is
even faster, but a lot of the speed is lost in the abstraction layers,
often leaving you with a fraction of the theoretical speed when you're
finally able to access the data.

When dealing with smaller amounts of data, this is not a problem, but 
when you're in the 100GB+ range, the overheads start to become painful,
meanwhile even consumer hardware is fully capable of dealing with these
quantities of data if you take an axe to all the crap between the hardware 
and the programmer. 

Slop is designed as a low abstraction what-you-see-is-what-you-do library, 
the reason for this is to be able to eliminate copies and other overheads 
that are common in higher level libraries.  

Additionally, many of the common tools Java offers for reading streams of 
data (e.g. InputStreams and most JDBC drivers) have shockingly bad performance, and 
the tools that let you do I/O faster tends to be finicky and hard to use.  

The function of Slop is essentially to let you write homogenous streams of
data to disk and read them back as fast as possible.

To avoid the common frustration of having multiple representations of 
the data in both the application and storage layers, a lot of what 
would commonly be kept in a schema description is instead just implemented 
as code by the library consumer, reducing the number of places where the 
schema is defined and limiting the number of times the data is copied 
or transformed.

To aid with portability, Slop stores schema information in the file names of the 
data files, besides the actual name of the column itself.   

A table of demographic information may end up stored in files like this:

```text
cities.0.dat.s8[].gz
cities.0.dat-len.varint.bin
population.0.dat.s32le.bin
average-age.0.dat.f64le.gz
```

(Endianness is specified in the file name because old and new Java utilities for dealing
with raw binary data have different default endianness and labelling the files makes it 
easier to know which is which.)

The Slop library offers a bare minimum of facilities to aid with data integrity, 
such as the SlopTable class, which is a wrapper that ensures consistent positions 
for a group of columns, and aids in closing the columns when they are no longer 
needed.  

Beyond that, you're largely on your own to ensure that the data is consistent.

## Why though?

Slop is fast.  

Slop generally outperforms most other storage formats available in Java
(e.g. anything over jdbc, parquet, protobuf) when it comes to sequential 
reads and writes, at the cost of really only supporting this use case.
A big part of why this is the case is because it offers a lot of flexibility 
in how the data is stored.

Slop is compact.

Depending on compression and encoding choices, the format will be smaller
than a parquet file containing the equivalent information.

Slop is simple.

There isn't much magic going on under the hood in Slop.  

It's designed with the philosophy that a competent programmer
should be able to reverse engineer the format of the data by 
just looking at a directory listing of the data files.  

There are no hidden indexes, magic numbers, no headers or footers, 
no block structures or checksums, no supplemental data besides 
the data as presented by `ls`.

Despite being a very obscure library, this gives the data a sort 
of portability.

## Example

With Slop it's desirable to keep the schema information in the code.  

The data is stored in a directory, and the data is written and read using the `MyData.Writer` and `MyData.Reader` classes.  
The `MyData` class is itself is a record, and the schema is stored as static fields in the `MyData` class.

```java
public record Population(String city, int population, double avgAge) {

    // This is the schema, and it's specified in code
    private static final StringColumn citiesColumn = new StringColumn("cities", StorageType.GZIP);
    private static final IntColumn populationColumn = new IntColumn("population", StorageType.PLAIN);
    private static final DoubleColumn averageAgeColumn = new DoubleColumn("average-age", StorageType.PLAIN);

    // Extend SlopTable to ensure that the columns are closed when the table is closed,
    // and adds basic sanity checks to ensure that the columns are in sync.
    public static class Writer extends SlopTable {
        private final StringColumn.Writer citiesWriter;
        private final IntColumn.Writer populationWriter;
        private final DoubleColumn.Writer avgAgeWriter;

        public Writer(Path baseDir) throws IOException {
            citiesWriter = citiesColumn.create(this, baseDir);
            populationWriter = populationColumn.create(this, baseDir);
            avgAgeWriter = averageAgeColumnn.create(this, baseDir);
        }

        public void write(Population data) throws IOException {
            citiesWriter.put(data.city);
            populationWriter.put(data.population);
            avgAgeWriter.put(data.avgAge);
        }
    }

    // Reader also extends SlopTable, for the same reasons as the Writer
    public static class Reader extends SlopTable {
        private final StringColumn.Reader citiesReader;
        private final IntColumn.Reader populationReader;
        private final DoubleColumn.Reader avgAgeReader;

        public Reader(Path baseDir) throws IOException {
            citiesReader = citiesColumn.open(this, baseDir);
            populationReader = populationColumn.open(this, baseDir);
            avgAgeReader = averageAgeColumnn.open(this, baseDir);
        }

        public boolean hasRemaining() throws IOException {
            return citiesReader.hasRemaining();
        }

        public Population read() throws IOException {
            return new Population(
                    citiesReader.get(),
                    populationReader.get(),
                    avgAgeReader.get()
            );
        }
    }
}
```

## Nested Records

Nested records are not supported in Slop, although array values are supported.  If you need to store nested records,
you've got the options of flattening them, representing them as arrays, or serializing them into a byte array and 
storing that.

## Paging

Slop supports splitting up the data into multiple files, which is useful for large datasets as these can be read independently
and in parallel.  It's also useful in batch processing, as each file can be processed to completion, allowing for resumption
of a terminated job without having to reprocess the entire dataset.

TBW

## Column Types

TBW

## Storage Types

TBW

## Extension

TBW

## SQL support

If you feel like Slop could benefit from SQL support, you're almost certainly looking at the wrong tool for the job.

