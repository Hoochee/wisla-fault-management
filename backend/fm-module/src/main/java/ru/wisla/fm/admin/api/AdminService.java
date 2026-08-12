package ru.wisla.fm.admin.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;
import ru.wisla.fm.cmdb.domain.ProductCiEntity;
import ru.wisla.fm.cmdb.domain.ProductCiId;
import ru.wisla.fm.cmdb.domain.ProductEntity;
import ru.wisla.fm.cmdb.persistence.ConfigurationItemRepository;
import ru.wisla.fm.cmdb.persistence.ProductCiRepository;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.cmdb.service.CmdbMapper;
import ru.wisla.fm.common.api.NotFoundException;
import ru.wisla.fm.common.api.PageMeta;
import ru.wisla.fm.common.security.AuthorizationService;
import ru.wisla.fm.identity.api.AuthService;
import ru.wisla.fm.identity.api.UserDto;
import ru.wisla.fm.identity.domain.RoleEntity;
import ru.wisla.fm.identity.domain.UserEntity;
import ru.wisla.fm.identity.persistence.RoleRepository;
import ru.wisla.fm.identity.persistence.UserRepository;
import ru.wisla.fm.processing.adapter.out.persistence.EventJpaRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ConfigurationItemRepository configurationItemRepository;
    private final ProductRepository productRepository;
    private final ProductCiRepository productCiRepository;
    private final EventJpaRepository eventRepository;
    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final PasswordEncoder passwordEncoder;
    private final CmdbMapper cmdbMapper;
    private final ObjectMapper objectMapper;

    public AdminService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        ConfigurationItemRepository configurationItemRepository,
                        ProductRepository productRepository,
                        ProductCiRepository productCiRepository,
                        EventJpaRepository eventRepository,
                        AuthService authService,
                        AuthorizationService authorizationService,
                        PasswordEncoder passwordEncoder,
                        CmdbMapper cmdbMapper,
                        ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.configurationItemRepository = configurationItemRepository;
        this.productRepository = productRepository;
        this.productCiRepository = productCiRepository;
        this.eventRepository = eventRepository;
        this.authService = authService;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
        this.cmdbMapper = cmdbMapper;
        this.objectMapper = objectMapper;
    }

    public UserPage listUsers(Boolean active, String search, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("login"));
        Specification<UserEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("login")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<UserEntity> result = userRepository.findAll(spec, pageable);
        return new UserPage(
                result.getContent().stream().map(authService::toDto).toList(),
                PageMeta.of(result.getNumber(), result.getSize(), result.getTotalElements())
        );
    }

    @Transactional
    public UserDto createUser(UserCreate request) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        if (userRepository.existsByLogin(request.login())) {
            throw new IllegalArgumentException("Login already exists");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        UserEntity user = new UserEntity();
        user.setLogin(request.login());
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setTeam(request.team());
        user.setActive(request.active() == null || request.active());
        user.setRoles(resolveRoles(request.roleIds()));
        return authService.toDto(userRepository.save(user));
    }

    public UserDto getUser(UUID id) {
        return authService.toDto(findUserOrThrow(id));
    }

    @Transactional
    public UserDto patchUser(UUID id, UserPatch patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        UserEntity user = findUserOrThrow(id);
        if (patch.fullName() != null) {
            user.setFullName(patch.fullName());
        }
        if (patch.email() != null) {
            if (userRepository.existsByEmail(patch.email()) && !patch.email().equals(user.getEmail())) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(patch.email());
        }
        if (patch.team() != null) {
            user.setTeam(patch.team());
        }
        if (patch.active() != null) {
            user.setActive(patch.active());
        }
        if (patch.password() != null && !patch.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(patch.password()));
        }
        if (patch.roleIds() != null) {
            user.setRoles(resolveRoles(patch.roleIds()));
        }
        return authService.toDto(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        UserEntity user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    public List<RoleDto> listRoles() {
        return roleRepository.findAll().stream().map(this::toRoleDto).toList();
    }

    @Transactional
    public RoleDto createRole(RoleCreate request) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        if (roleRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Role name already exists");
        }
        RoleEntity role = new RoleEntity();
        role.setName(request.name());
        role.setDescription(request.description());
        role.setPermissions(toJson(request.permissions()));
        return toRoleDto(roleRepository.save(role));
    }

    public RoleDto getRole(UUID id) {
        return toRoleDto(findRoleOrThrow(id));
    }

    @Transactional
    public RoleDto patchRole(UUID id, RolePatch patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        RoleEntity role = findRoleOrThrow(id);
        if (patch.name() != null) {
            if (roleRepository.existsByName(patch.name()) && !patch.name().equals(role.getName())) {
                throw new IllegalArgumentException("Role name already exists");
            }
            role.setName(patch.name());
        }
        if (patch.description() != null) {
            role.setDescription(patch.description());
        }
        if (patch.permissions() != null) {
            role.setPermissions(toJson(patch.permissions()));
        }
        return toRoleDto(roleRepository.save(role));
    }

    public ConfigurationItemPage listConfigurationItems(UUID productId, String tag, String search, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("fqdn"));
        Specification<ConfigurationItemEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fqdn")), pattern),
                        cb.like(cb.lower(root.get("systemName")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ConfigurationItemEntity> result = configurationItemRepository.findAll(spec, pageable);
        List<ConfigurationItemDto> items = result.getContent().stream()
                .map(ci -> cmdbMapper.toDto(ci, productCiRepository.findProductIdsByCiId(ci.getId())))
                .filter(ci -> productId == null || ci.products().contains(productId))
                .filter(ci -> tag == null || tag.isBlank() || ci.tags().contains(tag))
                .toList();
        return new ConfigurationItemPage(items, PageMeta.of(result.getNumber(), result.getSize(), result.getTotalElements()));
    }

    @Transactional
    public ConfigurationItemDto createConfigurationItem(ConfigurationItemCreate request) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        if (configurationItemRepository.existsByFqdn(request.fqdn())) {
            throw new IllegalArgumentException("FQDN already exists");
        }
        ConfigurationItemEntity ci = new ConfigurationItemEntity();
        ci.setFqdn(request.fqdn());
        ci.setCiType(request.ciType());
        ci.setSystemName(request.system());
        ci.setSubsystemName(request.subsystem());
        ci.setSoftware(request.software());
        ci.setTags(toJson(request.tags() != null ? request.tags() : List.of()));
        ci.setExternalIds(toJson(request.externalIds() != null ? request.externalIds() : Map.of()));
        configurationItemRepository.save(ci);
        linkProducts(ci.getId(), request.productIds());
        return cmdbMapper.toDto(ci, request.productIds() != null ? request.productIds() : List.of());
    }

    public ConfigurationItemDto getConfigurationItem(UUID id) {
        ConfigurationItemEntity ci = findCiOrThrow(id);
        return cmdbMapper.toDto(ci, productCiRepository.findProductIdsByCiId(id));
    }

    @Transactional
    public ConfigurationItemDto patchConfigurationItem(UUID id, ConfigurationItemPatch patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        ConfigurationItemEntity ci = findCiOrThrow(id);
        if (patch.fqdn() != null) {
            if (configurationItemRepository.existsByFqdn(patch.fqdn()) && !patch.fqdn().equals(ci.getFqdn())) {
                throw new IllegalArgumentException("FQDN already exists");
            }
            ci.setFqdn(patch.fqdn());
        }
        if (patch.ciType() != null) {
            ci.setCiType(patch.ciType());
        }
        if (patch.system() != null) {
            ci.setSystemName(patch.system());
        }
        if (patch.subsystem() != null) {
            ci.setSubsystemName(patch.subsystem());
        }
        if (patch.software() != null) {
            ci.setSoftware(patch.software());
        }
        if (patch.tags() != null) {
            ci.setTags(toJson(patch.tags()));
        }
        if (patch.externalIds() != null) {
            ci.setExternalIds(toJson(patch.externalIds()));
        }
        configurationItemRepository.save(ci);
        if (patch.productIds() != null) {
            productCiRepository.deleteByIdCiId(id);
            linkProducts(id, patch.productIds());
        }
        return cmdbMapper.toDto(ci, productCiRepository.findProductIdsByCiId(id));
    }

    @Transactional
    public void deleteConfigurationItem(UUID id) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        findCiOrThrow(id);
        if (eventRepository.existsByCiId(id)) {
            throw new IllegalStateException("Configuration item has dependent events");
        }
        productCiRepository.deleteByIdCiId(id);
        configurationItemRepository.deleteById(id);
    }

    public List<ProductAdminDto> listProductsAdmin() {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        return productRepository.findAll().stream()
                .map(this::toProductAdminDto)
                .toList();
    }

    public ProductAdminDto getProductAdmin(UUID id) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        return toProductAdminDto(findProductOrThrow(id));
    }

    @Transactional
    public ProductAdminDto createProduct(ProductCreate request) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        if (productRepository.existsByCode(request.code())) {
            throw new IllegalStateException("Product code already exists");
        }
        ProductEntity product = new ProductEntity();
        product.setName(request.name());
        product.setCode(request.code());
        product.setTenant(request.tenant());
        product.setSite(request.site());
        product.setTags(toJson(request.tags() != null ? request.tags() : List.of()));
        productRepository.save(product);
        if (request.ciIds() != null) {
            linkCisToProduct(product.getId(), request.ciIds());
        }
        return toProductAdminDto(product);
    }

    @Transactional
    public ProductAdminDto patchProduct(UUID id, ProductPatch patch) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        ProductEntity product = findProductOrThrow(id);
        if (patch.name() != null) {
            product.setName(patch.name());
        }
        if (patch.code() != null) {
            if (productRepository.existsByCode(patch.code()) && !patch.code().equals(product.getCode())) {
                throw new IllegalStateException("Product code already exists");
            }
            product.setCode(patch.code());
        }
        if (patch.tenant() != null) {
            product.setTenant(patch.tenant());
        }
        if (patch.site() != null) {
            product.setSite(patch.site());
        }
        if (patch.tags() != null) {
            product.setTags(toJson(patch.tags()));
        }
        productRepository.save(product);
        if (patch.ciIds() != null) {
            linkCisToProduct(id, patch.ciIds());
        }
        return toProductAdminDto(product);
    }

    @Transactional
    public void deleteProduct(UUID id) {
        authorizationService.requireAdmin(authorizationService.requireUserId());
        findProductOrThrow(id);
        if (!productCiRepository.findCiIdsByProductId(id).isEmpty()) {
            throw new IllegalStateException("Product has linked configuration items");
        }
        productRepository.deleteById(id);
    }

    private void linkCisToProduct(UUID productId, List<UUID> ciIds) {
        validateCiIdsExist(ciIds);
        productCiRepository.deleteByIdProductId(productId);
        if (ciIds == null) {
            return;
        }
        for (UUID ciId : ciIds) {
            ProductCiEntity link = new ProductCiEntity();
            link.setId(new ProductCiId(productId, ciId));
            productCiRepository.save(link);
        }
    }

    private void validateCiIdsExist(List<UUID> ciIds) {
        if (ciIds == null || ciIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = new HashSet<>(ciIds);
        long found = configurationItemRepository.findAllById(uniqueIds).size();
        if (found != uniqueIds.size()) {
            throw new IllegalArgumentException("One or more configuration items not found");
        }
    }

    private ProductAdminDto toProductAdminDto(ProductEntity product) {
        return new ProductAdminDto(
                product.getId(),
                product.getName(),
                product.getCode(),
                product.getTenant(),
                product.getSite(),
                parseStringList(product.getTags()),
                productCiRepository.findCiIdsByProductId(product.getId())
        );
    }

    private ProductEntity findProductOrThrow(UUID id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void linkProducts(UUID ciId, List<UUID> productIds) {
        if (productIds == null) {
            return;
        }
        for (UUID productId : productIds) {
            ProductCiEntity link = new ProductCiEntity();
            link.setId(new ProductCiId(productId, ciId));
            productCiRepository.save(link);
        }
    }

    private Set<RoleEntity> resolveRoles(List<UUID> roleIds) {
        Set<RoleEntity> roles = new HashSet<>();
        for (UUID roleId : roleIds) {
            roles.add(findRoleOrThrow(roleId));
        }
        return roles;
    }

    private UserEntity findUserOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private RoleEntity findRoleOrThrow(UUID id) {
        return roleRepository.findById(id).orElseThrow(() -> new NotFoundException("Role not found"));
    }

    private ConfigurationItemEntity findCiOrThrow(UUID id) {
        return configurationItemRepository.findById(id).orElseThrow(() -> new NotFoundException("Configuration item not found"));
    }

    private RoleDto toRoleDto(RoleEntity role) {
        return new RoleDto(role.getId(), role.getName(), role.getDescription(), parseStringList(role.getPermissions()));
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parseStringMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON value");
        }
    }
}
