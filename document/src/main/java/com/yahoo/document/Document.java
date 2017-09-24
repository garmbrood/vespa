// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.serialization.*;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.vespa.objects.BufferSerializer;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.Serializer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;

/**
 * A document is an identifiable
 * set of value bindings of a {@link DocumentType document type}.
 * A document represents an instance of some entity of interest
 * in an application, like an article, a web document, a product, etc.
 *
 * Deprecation: Try to use document set and get methods only with FieldValue types,
 * not with primitive types. Support for direct access to primitive types will
 * be removed soon.
 *
 * @author bratseth
 * @author Einar M R Rosenvinge
 */
public class Document extends StructuredFieldValue {

    public static final int classId = registerClass(Ids.document + 3, Document.class);
    public static final short SERIALIZED_VERSION = 8;
    private DocumentId docId;
    private StructuredFieldValue header;
    private StructuredFieldValue body;
    private Long lastModified = null;

    /**
     * Create a document with the given document type and identifier.
     * @param docType DocumentType to use for creation
     * @param id The id for this document
     */
    public Document(DocumentType docType, String id) {
        this(docType, new DocumentId(id));
    }

    /**
     * Create a document with the given document type and identifier.
     * @param docType DocumentType to use for creation
     * @param id The id for this document
     */
    public Document(DocumentType docType, DocumentId id) {
        super(docType);
        setNewType(docType);
        internalSetId(id, docType);
    }

    /**
     * Creates a document that is a shallow copy of another.
     *
     * @param doc The document to copy.
     */
    public Document(Document doc) {
        this(doc.getDataType(), doc.getId());
        header = doc.header;
        body = doc.body;
        lastModified = doc.lastModified;
    }

    /**
     *
     * @param reader The deserializer to use for creating this document
     */
    public Document(DocumentReader reader) {
        super(null);
        reader.read(this);
    }

    public DocumentId getId() { return docId; }
    public void setId(DocumentId id) { internalSetId(id, getDataType()); }
    private void internalSetId(DocumentId id, DocumentType docType) {
        if (id != null && id.hasDocType() && docType != null && !id.getDocType().equals(docType.getName())) {
            throw new IllegalArgumentException("Trying to set a document id (type " + id.getDocType() +
                                               ") that doesn't match the document type (" + getDataType().getName() + ").");
        }
        docId = id;
    }

    public StructuredFieldValue getHeader() { return header; }
    public StructuredFieldValue getBody() { return body; }

    @Override
    public void assign(Object o) {
        throw new IllegalArgumentException("Assign not implemented for " + getClass() + " objects");
    }

    @Override
    public Document clone() {
        Document doc = (Document) super.clone();
        doc.docId = docId.clone();
        doc.header = header.clone();
        doc.body = body.clone();
        return doc;
    }

    private void setNewType(DocumentType type) {
        header = type.getHeaderType().createFieldValue();
        body = type.getBodyType().createFieldValue();
    }

    public void setDataType(DataType type) {
        if (docId != null && docId.hasDocType() && !docId.getDocType().equals(type.getName())) {
            throw new IllegalArgumentException("Trying to set a document type (" + type.getName() +
                                               ") that doesn't match the document id (" + docId + ").");
        }
        super.setDataType(type);
        setNewType((DocumentType)type);
    }

    public int getSerializedSize() throws SerializationException {
        DocumentSerializer data = DocumentSerializerFactory.create42(new GrowableByteBuffer(64 * 1024, 2.0f));
        data.write(this);
        return data.getBuf().position();
    }

    /**
     *  This is an approximation of serialized size. We just set it to 4096 as a definition of a medium document.
     * @return Approximate size of document (4096)
     */
    public final int getApproxSize() { return 4096; }

    public void serialize(OutputStream out) throws SerializationException {
        DocumentSerializer writer = DocumentSerializerFactory.create42(new GrowableByteBuffer(64 * 1024, 2.0f));
        writer.write(this);
        GrowableByteBuffer data = writer.getBuf();
        byte[] array;
        if (data.hasArray()) {
            //just get the array
            array = data.array();
        } else {
            //copy the bytebuffer into the array
            array = new byte[data.position()];
            int endPos = data.position();
            data.position(0);
            data.get(array);
            data.position(endPos);
        }
        try {
            out.write(array, 0, data.position());
        } catch (IOException ioe) {
            throw new SerializationException(ioe);
        }
    }

    public static Document createDocument(DocumentReader buffer) {
        return new Document(buffer);
    }

    @Override
    public Field getField(String fieldName) {
        Field field = header.getField(fieldName);
        if (field == null) {
            field = body.getField(fieldName);
        }
        if (field == null) {
            for(DocumentType parent : getDataType().getInheritedTypes()) {
                field = parent.getField(fieldName);
                if (field != null) {
                    break;
                }
            }
        }
        return field;
    }

    @Override
    public FieldValue getFieldValue(Field field) {
        if (field.isHeader()) {
            return header.getFieldValue(field);
        } else {
            return body.getFieldValue(field);
        }
    }

    @Override
    protected void doSetFieldValue(Field field, FieldValue value) {
        if (field.isHeader()) {
            header.setFieldValue(field, value);
        } else {
            body.setFieldValue(field, value);
        }
    }

    @Override
    public FieldValue removeFieldValue(Field field) {
        if (field.isHeader()) {
            return header.removeFieldValue(field);
        } else {
            return body.removeFieldValue(field);
        }
    }

    @Override
    public void clear() {
        header.clear();
        body.clear();
    }

    @Override
    public Iterator<Map.Entry<Field, FieldValue>> iterator() {
        return new Iterator<Map.Entry<Field, FieldValue>>() {

            private Iterator<Map.Entry<Field, FieldValue>> headerIt = header.iterator();
            private Iterator<Map.Entry<Field, FieldValue>> bodyIt = body.iterator();

            public boolean hasNext() {
                if (headerIt != null) {
                    if (headerIt.hasNext()) {
                        return true;
                    } else {
                        headerIt = null;
                    }
                }
                return bodyIt.hasNext();
            }

            public Map.Entry<Field, FieldValue> next() {
                return (headerIt == null ? bodyIt.next() : headerIt.next());
            }

            public void remove() {
                if (headerIt == null) {
                    bodyIt.remove();
                } else {
                    headerIt.remove();
                }
            }
        };
    }

    public String toString() {
        return "document '" + String.valueOf(docId) + "' of type '" + getDataType().getName() + "'";
    }

    public String toXML(String indent) {
        XmlStream xml = new XmlStream();
        xml.setIndent(indent);
        xml.beginTag("document");
        printXml(xml);
        xml.endTag();
        return xml.toString();
    }

    /**
     * Get XML representation of the document root and its children, contained
     * within a &lt;document&gt;&lt;/document&gt; tag.
     * @return XML representation of document
     */
    public String toXml() {
        return toXML("  ");
    }

    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printDocumentXml(this, xml);
    }

    /** Returns true if the argument is a document which has the same set of values */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof Document)) return false;
        Document other = (Document) o;
        return (super.equals(o) && docId.equals(other.docId) &&
                header.equals(other.header) && body.equals(other.body));
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + (docId != null ? docId.hashCode() : 0);
    }

    /**
     * Returns the last modified time of this Document, when stored in persistent storage. This is typically set by the
     * library that retrieves the Document from persistent storage.
     *
     * This variable doesn't really belong in document. It is used when retrieving docblocks of documents to be able to
     * see when documents was last modified in VDS, without having to add modified times separate in the API.
     *
     * NOTE: This is a transient field, and will not be serialized with a Document (will be null after deserialization).
     *
     * @return the last modified time of this Document (in milliseconds), or null if unset
     */
    public Long getLastModified() {
        return lastModified;
    }

    /**
     * Sets the last modified time of this Document. This is typically set by the library that retrieves the
     * Document from persistent storage, and should not be set by arbitrary clients. NOTE: This is a
     * transient field, and will not be serialized with a Document (will be null after deserialization).
     *
     * @param lastModified the last modified time of this Document (in milliseconds)
     */
    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    public void onSerialize(Serializer data) throws SerializationException {
        serialize((DocumentWriter)data);
    }

    @SuppressWarnings("deprecation")
    public void serializeHeader(Serializer data) throws SerializationException {
        if (data instanceof DocumentWriter) {
            if (data instanceof VespaDocumentSerializer42) {
                ((VespaDocumentSerializer42)data).setHeaderOnly(true);
            }
            serialize((DocumentWriter)data);
        } else if (data instanceof BufferSerializer) {
            serialize(DocumentSerializerFactory.create42(((BufferSerializer) data).getBuf(), true));
        } else {
            DocumentSerializer fw = DocumentSerializerFactory.create42(new GrowableByteBuffer(), true);
            serialize(fw);
            data.put(null, fw.getBuf().getByteBuffer());
        }
    }

    public void serializeBody(Serializer data) throws SerializationException {
        if (getBody().getFieldCount() > 0) {
            if (data instanceof FieldWriter) {
                getBody().serialize(new Field("body", getBody().getDataType()), (FieldWriter) data);
            } else if (data instanceof BufferSerializer) {
                getBody().serialize(new Field("body", getBody().getDataType()), DocumentSerializerFactory.create42(((BufferSerializer) data).getBuf()));
            } else {
                DocumentSerializer fw = DocumentSerializerFactory.create42(new GrowableByteBuffer());
                getBody().serialize(new Field("body", getBody().getDataType()), fw);
                data.put(null, fw.getBuf().getByteBuffer());
            }
        }
    }

    @Override
    public DocumentType getDataType() {
        return (DocumentType)super.getDataType();
    }

    @Override
    public int getFieldCount() {
        return header.getFieldCount() + body.getFieldCount();
    }

    public void serialize(DocumentWriter writer) {
        writer.write(this);
    }

    public void deserialize(DocumentReader reader) {
        reader.read(this);
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    /* (non-Javadoc)
     * @see com.yahoo.document.datatypes.FieldValue#deserialize(com.yahoo.document.Field, com.yahoo.document.serialization.FieldReader)
     */
    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        Document otherValue = (Document) fieldValue;
        comp = getId().compareTo(otherValue.getId());

        if (comp != 0) {
            return comp;
        }

        comp = header.compareTo(otherValue.header);

        if (comp != 0) {
            return comp;
        }

        comp = body.compareTo(otherValue.body);
        return comp;
    }

}
