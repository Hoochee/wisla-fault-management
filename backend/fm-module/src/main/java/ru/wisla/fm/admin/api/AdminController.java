package ru.wisla.fm.admin.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.wisla.fm.identity.api.UserDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/users")
    public UserPage listUsers(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminService.listUsers(active, search, page, size);
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(request));
    }

    @GetMapping("/users/{id}")
    public UserDto getUser(@PathVariable UUID id) {
        return adminService.getUser(id);
    }

    @PatchMapping("/users/{id}")
    public UserDto patchUser(@PathVariable UUID id, @RequestBody UserPatch patch) {
        return adminService.patchUser(id, patch);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    public List<RoleDto> listRoles() {
        return adminService.listRoles();
    }

    @PostMapping("/roles")
    public ResponseEntity<RoleDto> createRole(@Valid @RequestBody RoleCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createRole(request));
    }

    @GetMapping("/roles/{id}")
    public RoleDto getRole(@PathVariable UUID id) {
        return adminService.getRole(id);
    }

    @PatchMapping("/roles/{id}")
    public RoleDto patchRole(@PathVariable UUID id, @RequestBody RolePatch patch) {
        return adminService.patchRole(id, patch);
    }

    @GetMapping("/configuration-items")
    public ConfigurationItemPage listConfigurationItems(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminService.listConfigurationItems(productId, tag, search, page, size);
    }

    @PostMapping("/configuration-items")
    public ResponseEntity<ConfigurationItemDto> createConfigurationItem(
            @Valid @RequestBody ConfigurationItemCreate request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createConfigurationItem(request));
    }

    @GetMapping("/configuration-items/{id}")
    public ConfigurationItemDto getConfigurationItem(@PathVariable UUID id) {
        return adminService.getConfigurationItem(id);
    }

    @PatchMapping("/configuration-items/{id}")
    public ConfigurationItemDto patchConfigurationItem(
            @PathVariable UUID id,
            @RequestBody ConfigurationItemPatch patch
    ) {
        return adminService.patchConfigurationItem(id, patch);
    }

    @DeleteMapping("/configuration-items/{id}")
    public ResponseEntity<Void> deleteConfigurationItem(@PathVariable UUID id) {
        adminService.deleteConfigurationItem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public List<ProductAdminDto> listProducts() {
        return adminService.listProductsAdmin();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductAdminDto> createProduct(@Valid @RequestBody ProductCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createProduct(request));
    }

    @GetMapping("/products/{id}")
    public ProductAdminDto getProduct(@PathVariable UUID id) {
        return adminService.getProductAdmin(id);
    }

    @PatchMapping("/products/{id}")
    public ProductAdminDto patchProduct(@PathVariable UUID id, @RequestBody ProductPatch patch) {
        return adminService.patchProduct(id, patch);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID id) {
        adminService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
