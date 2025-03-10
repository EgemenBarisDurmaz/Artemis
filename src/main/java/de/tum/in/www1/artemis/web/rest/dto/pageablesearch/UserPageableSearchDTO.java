package de.tum.in.www1.artemis.web.rest.dto.pageablesearch;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserPageableSearchDTO extends SearchTermPageableSearchDTO<String> {

    /**
     * Set of authorities users need to match.
     */
    private Set<String> authorities = new HashSet<>();

    /**
     * Set of origins users need to match.
     */
    private Set<String> origins = new HashSet<>();

    /**
     * Set of status users need to match.
     */
    private Set<String> status = new HashSet<>();

    /**
     * Set of courseIds users need to be part in.
     */
    private Set<Long> courseIds = new HashSet<>();

    /**
     * Set of registrationNumbers users need to match
     */
    private Set<String> registrationNumbers;

    public Set<String> getAuthorities() {
        return authorities;
    }

    public void setAuthorities(Set<String> authorities) {
        this.authorities = authorities;
    }

    public Set<String> getOrigins() {
        return origins;
    }

    public void setOrigins(Set<String> origins) {
        this.origins = origins;
    }

    public Set<String> getStatus() {
        return status;
    }

    public void setStatus(Set<String> status) {
        this.status = status;
    }

    public Set<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(Set<Long> courseIds) {
        this.courseIds = courseIds;
    }

    public Set<String> getRegistrationNumbers() {
        return registrationNumbers;
    }

    public void setRegistrationNumbers(Set<String> registrationNumbers) {
        this.registrationNumbers = registrationNumbers;
    }
}
