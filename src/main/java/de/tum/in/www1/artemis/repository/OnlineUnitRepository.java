package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Online Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface OnlineUnitRepository extends JpaRepository<OnlineUnit, Long> {

    @NotNull
    default OnlineUnit findByIdElseThrow(Long onlineUnitId) throws EntityNotFoundException {
        return findById(onlineUnitId).orElseThrow(() -> new EntityNotFoundException("OnlineUnit", onlineUnitId));
    }

}
