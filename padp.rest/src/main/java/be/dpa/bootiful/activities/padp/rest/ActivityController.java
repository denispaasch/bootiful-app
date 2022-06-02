package be.dpa.bootiful.activities.padp.rest;

import be.dpa.bootiful.activities.dm.api.Activity;
import be.dpa.bootiful.activities.dm.api.ActivityRequest;
import be.dpa.bootiful.activities.dm.api.IActivityService;
import be.dpa.bootiful.activities.dm.api.Participant;
import be.dpa.bootiful.activities.dm.api.ParticipantRequest;
import be.dpa.bootiful.activities.dm.api.exception.ActivityNotFoundException;
import be.dpa.bootiful.activities.dm.api.exception.InvalidParticipantException;
import be.dpa.bootiful.activities.padp.rest.validation.SearchConstraint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static be.dpa.bootiful.activities.padp.rest.util.RelationConstants.RELATION_ACTIVITIES;
import static be.dpa.bootiful.activities.padp.rest.util.RelationConstants.RELATION_ACTIVITY;
import static be.dpa.bootiful.activities.padp.rest.util.RelationConstants.RELATION_PARTICIPANTS;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Controller for the activities REST API.
 *
 * @author denis
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Validated
class ActivityController {

    private final IActivityService activityService;

    private final RelationService relationService;

    private final PagedResourcesAssembler<Activity> activityPagedResourcesAssembler;

    private final PagedResourcesAssembler<Participant> participantPagedResourcesAssembler;

    @ExceptionHandler(value = { ConstraintViolationException.class,
        ActivityNotFoundException.class, InvalidParticipantException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<String> handleException(Exception e, WebRequest request) {
        return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @Operation(summary = "Gets a paged model containing activities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "A paged model of activities", content =
            {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PagedModel.class))}),
        @ApiResponse(responseCode = "204",
                description = "Sadly there are no activities yet")
    })
    @GetMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<PagedModel<Activity>> getActivities(
            @Valid @SearchConstraint @Parameter(description = "An optional search string, f.e. type==busywork")
            @RequestParam(defaultValue = "") String search,
            @Parameter(description = "The page index") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(description = "The page size") @RequestParam(defaultValue = "5") Integer size) {
        Optional<String> optSearch = StringUtils.isEmpty(search) ? Optional.empty() : Optional.of(search);
        Page<Activity> activities = activityService.getActivities(optSearch, page, size);
        List<Activity> content = activities.getContent();
        if (CollectionUtils.isEmpty(content)) {
            return ResponseEntity.noContent().build();
        }
        content.forEach(this::addActivityLinks);
        return ResponseEntity.ok(activityPagedResourcesAssembler.toModel(activities, a -> a));
    }

    @Operation(summary = "Gets an activity by its alternate key")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Found the activity", content =
            {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Activity.class))}),
        @ApiResponse(responseCode = "404", description = "Activity not found")})
    @GetMapping(value = "/{alternateKey}", produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Activity> getActivityBy(@Parameter(description = "The alternate key of the activity")
        @PathVariable String alternateKey) {
        Optional<Activity> optResponse = activityService.getActivityBy(alternateKey);
        Activity activity = optResponse.orElse(null);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }
        addActivityLinks(activity);
        return ResponseEntity.ok(activity);
    }

    private void addParticipantLinks(Participant participant, String alternateKey) {
        Link activityLink = linkTo(methodOn(ActivityController.class)
                .getActivityBy(alternateKey)).withRel(RELATION_ACTIVITY);
        Link activitiesLink = linkTo(methodOn(ActivityController.class)
                .getActivities(null, null, null)).withRel(RELATION_ACTIVITIES).expand();
        participant.add(activityLink, activitiesLink);
    }

    @Operation(summary = "Gets the participants of a specific activity, f.e. a public facebook party")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "A paged model of activity participants", content =
            {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = PagedModel.class))}),
        @ApiResponse(responseCode = "204", description = "Sadly there are participants for the given activity")
    })
    @GetMapping(value = "/{alternateKey}/participants",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<PagedModel<Participant>> getParticipantsBy(@PathVariable String alternateKey,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "5") Integer size) {
        Page<Participant> participants = activityService.getActivityParticipants(alternateKey, page, size);
        List<Participant> content = participants.getContent();
        if (CollectionUtils.isEmpty(content)) {
            return ResponseEntity.noContent().build();
        }
        content.forEach(participant -> addParticipantLinks(participant, alternateKey));
        return ResponseEntity.ok(participantPagedResourcesAssembler.toModel(participants, p -> p));
    }

    @Operation(summary = "Creates a participant for a specific activity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created the participant", content =
            {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = Participant.class))}),
        @ApiResponse(responseCode = "400", description = "Invalid participant request")})
    @PostMapping(value = "/{alternateKey}/participants",
            produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> newParticipant(@PathVariable String alternateKey,
                                            @Parameter(description = "The participant to create")
                                            @Valid @RequestBody ParticipantRequest participantRequest)
            throws InvalidParticipantException, ActivityNotFoundException {
        Participant participant = activityService.newParticipant(alternateKey, participantRequest);
        Optional<URI> participantUri = relationService.convertToUri(
                participant.getRequiredLink(IanaLinkRelations.SELF));
        if (participantUri.isPresent()) {
            return ResponseEntity.created(participantUri.get()).body(participant);
        }
        return ResponseEntity.badRequest().body(
                String.format("Failed to create URI to new participant with alternate key %s",
                        participant.getAlternateKey()));
    }

    private void addActivityLinks(Activity activity) {
        Link selfLink = linkTo(methodOn(ActivityController.class)
                .getActivityBy(activity.getAlternateKey())).withSelfRel();
        Link participantsLink = linkTo(methodOn(ActivityController.class)
                .getParticipantsBy(activity.getAlternateKey(), null, null)).withRel(RELATION_PARTICIPANTS).expand();
        Link activitiesLink = linkTo(methodOn(ActivityController.class)
                .getActivities(null, null, null)).withRel(RELATION_ACTIVITIES).expand();
        activity.add(selfLink, participantsLink, activitiesLink);
    }

    @Operation(summary = "Creates an activity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Created the activity", content =
                {@Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = Activity.class))}),
        @ApiResponse(responseCode = "400", description = "Invalid activity request")})
    @PostMapping(produces = {MediaTypes.HAL_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<?> newActivity(@Parameter(description = "The activity to create")
        @Valid @RequestBody ActivityRequest activityRequest) {
        Activity activity = activityService.newActivity(activityRequest);
        addActivityLinks(activity);
        Optional<URI> activityUri = relationService.convertToUri(
                activity.getRequiredLink(IanaLinkRelations.SELF));
        if (activityUri.isPresent()) {
            return ResponseEntity.created(activityUri.get()).body(activity);
        }
        return ResponseEntity.badRequest().body(
                String.format("Failed to create URI to new activity with alternate key %s",
                        activity.getAlternateKey()));
    }

    @Operation(summary = "Updates an activity")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Updated the activity"),
        @ApiResponse(responseCode = "400", description = "Invalid activity request")})
    @PutMapping(value = "/{alternateKey}")
    public ResponseEntity<?> updateActivity(@Parameter(description = "The activity data to update")
                                            @Valid @RequestBody ActivityRequest activityRequest,
                                            @Parameter(description = "The alternate key of the activity to update")
                                            @PathVariable String alternateKey) {
        activityService.updateActivity(alternateKey, activityRequest);
        Link activityLink = linkTo(methodOn(ActivityController.class).getActivityBy(alternateKey)).withSelfRel();
        Optional<URI> activityUri = relationService.convertToUri(activityLink);
        if (activityUri.isPresent()) {
            return ResponseEntity.noContent().location(activityUri.get()).build();
        }
        return ResponseEntity.badRequest().body(
                String.format("Failed to create URI to updated activity with alternate key %s", alternateKey));
    }

    @Operation(summary = "Deletes an activity by its alternate key")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted the activity",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
            schema = @Schema(implementation = Activity.class))),
        @ApiResponse(responseCode = "404", description = "Activity to delete not found", content = @Content)})
    @DeleteMapping(value = "/{alternateKey}")
    public ResponseEntity<?> deleteActivity(@Parameter(description = "The alternate key of the activity to delete")
                                            @PathVariable String alternateKey) {
        if (activityService.deleteActivity(alternateKey)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

}
