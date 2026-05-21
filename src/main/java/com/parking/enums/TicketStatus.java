package com.parking.enums;

/**
 * Lifecycle states of a parking ticket.
 */
public enum TicketStatus {
    ACTIVE,   // Vehicle is parked; ticket is open
    PAID,     // Fee has been paid; vehicle may exit
    EXITED,   // Vehicle has left; ticket is closed
    LOST      // Ticket was reported lost (flat fee applies)
}