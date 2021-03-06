// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/messagebus/errorcode.h>
#include "retrytransienterrorspolicy.h"

namespace mbus {

RetryTransientErrorsPolicy::RetryTransientErrorsPolicy() :
    _enabled(true),
    _baseDelay(1)
{ }

RetryTransientErrorsPolicy &
RetryTransientErrorsPolicy::setEnabled(bool enabled)
{
    _enabled = enabled;
    return *this;
}

RetryTransientErrorsPolicy &
RetryTransientErrorsPolicy::setBaseDelay(double baseDelay)
{
    _baseDelay = baseDelay;
    return *this;
}

bool
RetryTransientErrorsPolicy::canRetry(uint32_t errorCode) const
{
    return _enabled && errorCode < ErrorCode::FATAL_ERROR;
}

double
RetryTransientErrorsPolicy::getRetryDelay(uint32_t retry) const
{
    return _baseDelay * retry;
}

} // namespace mbus

