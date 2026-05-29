package com.parking;

import com.parking.db.CustomerRepository;
import com.parking.enums.VehicleType;
import com.parking.model.ParkingLot;
import com.parking.model.Vehicle;
import com.parking.service.FeeCalculator;
import com.parking.service.TicketManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CustomerRepository Tests")
class CustomerRepositoryTest {

    @BeforeEach
    void setUp() {
        ParkingLot.resetInstance();
    }

    @Test
    @DisplayName("Customer name and phone can be updated after ticket issue")
    void updateCustomerInfo_afterTicketIssue() {
        String plate = "QA" + String.format("%06d", Math.abs(System.nanoTime()) % 1_000_000);
        TicketManager manager = new TicketManager(new FeeCalculator());
        manager.issueTicket(new Vehicle(plate, VehicleType.CAR));

        CustomerRepository repo = new CustomerRepository();
        repo.updateCustomerInfo(plate, "Nino Test", "+995555000000");
        CustomerRepository.CustomerRecord customer = repo.findByPlate(plate);

        assertNotNull(customer);
        assertEquals("Nino Test", customer.fullName);
        assertEquals("+995555000000", customer.phone);
        assertEquals(plate, customer.licensePlate);
        assertEquals("CAR", customer.vehicleType);
    }
}
