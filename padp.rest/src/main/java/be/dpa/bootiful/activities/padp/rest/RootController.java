package be.dpa.bootiful.activities.padp.rest;

import be.dpa.bootiful.activities.dm.api.RootResponse;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Root entry point controller to access the bootiful activities.
 *
 * @author denis
 */
@RestController
@RequestMapping("/api/v1")
class RootController {

    /**
     * Gets the root entry point to access the bootiful activities.
     *
     * @return the root response
     */
    @GetMapping(produces = MediaTypes.HAL_JSON_VALUE)
    public ResponseEntity<RootResponse> getRoot() {
        RootResponse rootResponse = new RootResponse();
        Link activitiesLink = linkTo(methodOn(ActivityController.class).getActivities()).withRel("activities");
        rootResponse.add(activitiesLink);
        return ResponseEntity.ok(rootResponse);
    }
}
