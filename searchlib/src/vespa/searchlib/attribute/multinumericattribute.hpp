// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/attribute/multivalueattribute.hpp>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/fastlib/io/bufferedfile.h>
#include "multinumericattributesaver.h"
#include "load_utils.h"

namespace search {

template <typename B, typename M>
typename MultiValueNumericAttribute<B, M>::T
MultiValueNumericAttribute<B, M>::getFromEnum(EnumHandle e) const
{
    (void) e;
    return 0;
}

template <typename B, typename M>
bool MultiValueNumericAttribute<B, M>::findEnum(T value, EnumHandle & e) const
{
    (void) value; (void) e;
    return false;
}

template <typename B, typename M>
MultiValueNumericAttribute<B, M>::
MultiValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c) :
    MultiValueAttribute<B, M>(baseFileName, c)
{
}

template <typename B, typename M>
uint32_t MultiValueNumericAttribute<B, M>::getValueCount(DocId doc) const
{
    if (doc >= B::getNumDocs()) {
        return 0;
    }
    return this->_mvMapping.getValueCount(doc);
}

template <typename B, typename M>
void
MultiValueNumericAttribute<B, M>::onCommit()
{
    DocumentValues docValues;
    this->applyAttributeChanges(docValues);
    {
        typename B::ValueModifier valueGuard(this->getValueModifier());
        for (const auto & value : docValues) {
            clearOldValues(value.first);
            setNewValues(value.first, value.second);
        }
    }

    std::atomic_thread_fence(std::memory_order_release);
    this->removeAllOldGenerations();

    this->_changes.clear();
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::onUpdateStat()
{
    MemoryUsage usage = this->_mvMapping.getMemoryUsage();
    this->updateStatistics(this->_mvMapping.getTotalValueCnt(), this->_mvMapping.getTotalValueCnt(), usage.allocatedBytes(),
                           usage.usedBytes(), usage.deadBytes(), usage.allocatedBytesOnHold());
}


template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::clearOldValues(DocId doc)
{
    (void) doc;
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::setNewValues(DocId doc, const std::vector<WType> & values)
{
    this->_mvMapping.set(doc, values);
}

template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::removeOldGenerations(generation_t firstUsed)
{
    this->_mvMapping.trimHoldLists(firstUsed);
}


template <typename B, typename M>
void MultiValueNumericAttribute<B, M>::onGenerationChange(generation_t generation)
{
    this->_mvMapping.transferHoldLists(generation - 1);
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::onLoadEnumerated(typename B::ReaderBase &
                                                   attrReader)
{
    uint32_t numDocs = attrReader.getNumIdx() - 1;
    this->setNumDocs(numDocs);
    this->setCommittedDocIdLimit(numDocs);

    FileUtil::LoadedBuffer::UP udatBuffer(this->loadUDAT());
    assert((udatBuffer->size() % sizeof(T)) == 0);
    vespalib::ConstArrayRef<T> map(reinterpret_cast<const T *>(udatBuffer->buffer()),
                                   udatBuffer->size() / sizeof(T));
    uint32_t maxvc = attribute::loadFromEnumeratedMultiValue(this->_mvMapping, attrReader, map, attribute::NoSaveLoadedEnum());
    this->checkSetMaxValueCount(maxvc);
    
    return true;
}

template <typename B, typename M>
bool
MultiValueNumericAttribute<B, M>::onLoad()
{
    typename B::template PrimitiveReader<MValueType> attrReader(*this);
    bool ok(attrReader.getHasLoadData());

    if (!ok)
        return false;

    this->setCreateSerialNum(attrReader.getCreateSerialNum());

    if (attrReader.getEnumerated())
        return onLoadEnumerated(attrReader);
    
    bool hasWeight(attrReader.hasWeight());
    size_t numDocs = attrReader.getNumIdx() - 1;

    this->_mvMapping.prepareLoadFromMultiValue(attrReader);
    // set values
    std::vector<MultiValueType> values;
    B::setNumDocs(numDocs);
    B::setCommittedDocIdLimit(numDocs);
    for (DocId doc = 0; doc < numDocs; ++doc) {
        const uint32_t valueCount(attrReader.getNextValueCount());
        for (uint32_t i(0); i < valueCount; i++) {
            MValueType currData = attrReader.getNextData();
            values.push_back(MultiValueType(currData,
                                            hasWeight ?
                                            attrReader.getNextWeight() : 1));
        }
        this->checkSetMaxValueCount(valueCount);
        setNewValues(doc, values);
        values.clear();
    }
    return true;
}

template <typename B, typename M>
AttributeVector::SearchContext::UP
MultiValueNumericAttribute<B, M>::getSearch(QueryTermSimple::UP qTerm,
                                            const AttributeVector::SearchContext::Params & params) const
{
    (void) params;
    if (this->hasArrayType()) {
        return std::unique_ptr<AttributeVector::SearchContext>
            (new ArraySearchContext(std::move(qTerm), *this));
    } else {
        return std::unique_ptr<AttributeVector::SearchContext>
            (new SetSearchContext(std::move(qTerm), *this));
    }
}


template <typename B, typename M>
std::unique_ptr<AttributeSaver>
MultiValueNumericAttribute<B, M>::onInitSave()
{
    vespalib::GenerationHandler::Guard guard(this->getGenerationHandler().
                                             takeGuard());
    return std::make_unique<MultiValueNumericAttributeSaver<MultiValueType,
        typename M::Index>>
        (std::move(guard), this->createSaveTargetConfig(), this->_mvMapping);
}

} // namespace search
