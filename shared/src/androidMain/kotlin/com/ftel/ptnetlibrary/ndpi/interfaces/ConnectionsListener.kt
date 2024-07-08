package com.ftel.ptnetlibrary.ndpi.interfaces

import com.ftel.ptnetlibrary.ndpi.models.ConnectionDescriptor


interface ConnectionsListener {
    fun connectionsChanges(numOfConnection: Int)
    fun connectionsAdded(start: Int, descriptorArray: Array<ConnectionDescriptor>?)
    fun connectionsRemoved(start: Int, descriptorArray: Array<ConnectionDescriptor>?)
    fun connectionsUpdated(positions: IntArray?)
}