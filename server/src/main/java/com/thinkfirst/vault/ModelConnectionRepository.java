package com.thinkfirst.vault;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ModelConnectionRepository extends JpaRepository<ModelConnection,Long>{
 List<ModelConnection> findByOwnerIdOrderByIdAsc(Long ownerId);
 Optional<ModelConnection> findByOwnerIdAndProvider(Long ownerId,String provider);
}
