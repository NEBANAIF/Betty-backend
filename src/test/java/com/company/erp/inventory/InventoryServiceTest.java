package com.company.erp.inventory;

import com.company.erp.common.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceTest {

    private final InventoryService service = new InventoryService(null);

    @Test
    void increaseAddsToQuantityAndReturnsPreviousValue() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(400);

        int previous = service.increase(inventory, 100);

        assertThat(previous).isEqualTo(400);
        assertThat(inventory.getQuantity()).isEqualTo(500);
    }

    @Test
    void decreaseSubtractsAndReturnsPreviousValue() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(500);

        int previous = service.decrease(inventory, 100);

        assertThat(previous).isEqualTo(500);
        assertThat(inventory.getQuantity()).isEqualTo(400);
    }

    @Test
    void decreaseBeyondAvailableQuantityThrows() {
        Inventory inventory = new Inventory();
        inventory.setQuantity(50);
        inventory.setReservedQuantity(10); // available = 40

        assertThatThrownBy(() -> service.decrease(inventory, 41))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(inventory.getQuantity()).isEqualTo(50); // unchanged on failure
    }
}
