/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.notes;

import com.example.notes.NoteResourceAssembler.NoteResource;
import com.example.notes.TagResourceAssembler.TagResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriTemplate;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;


public class NotesController {

	private static final UriTemplate TAG_URI_TEMPLATE = new UriTemplate("/tags/{id}");

	private final NoteRepository noteRepository;

	private final TagRepository tagRepository;

	private final NoteResourceAssembler noteResourceAssembler;

	private final TagResourceAssembler tagResourceAssembler;

	@Autowired
	public NotesController(NoteRepository noteRepository, TagRepository tagRepository,
			NoteResourceAssembler noteResourceAssembler,
			TagResourceAssembler tagResourceAssembler) {
		this.noteRepository = noteRepository;
		this.tagRepository = tagRepository;
		this.noteResourceAssembler = noteResourceAssembler;
		this.tagResourceAssembler = tagResourceAssembler;
	}

	@RequestMapping(method = RequestMethod.GET)
	NestedContentResource<NoteResource> all() {
		return new NestedContentResource<NoteResource>(
				this.noteResourceAssembler.toResources(this.noteRepository.findAll()));
	}

	@ResponseStatus(HttpStatus.CREATED)
	@RequestMapping(method = RequestMethod.POST)
	HttpHeaders create(@RequestBody NoteInput noteInput) {
		Note note = new Note();
		note.setTitle(noteInput.getTitle());
		note.setBody(noteInput.getBody());
		note.setTags(getTags(noteInput.getTagUris()));

		this.noteRepository.save(note);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders
				.setLocation(linkTo(NotesController.class).slash(note.getId()).toUri());

		return httpHeaders;
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	void delete(@PathVariable("id") long id) {
		this.noteRepository.delete(id);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	Resource<Note> note(@PathVariable("id") long id) {
		Note note = this.noteRepository.findById(id).orElseThrow(
				() -> new ResourceDoesNotExistException());
		return this.noteResourceAssembler.toResource(note);
	}

	@RequestMapping(value = "/{id}/tags", method = RequestMethod.GET)
	ResourceSupport noteTags(@PathVariable("id") long id) {
		return new NestedContentResource<TagResource>(
				this.tagResourceAssembler.toResources(this.noteRepository
						.findById(id)
						.orElseThrow(
								() -> new ResourceDoesNotExistException()).getTags()));
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.PATCH)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void updateNote(@PathVariable("id") long id, @RequestBody NotePatchInput noteInput) {
		Note note = this.noteRepository.findById(id).orElseThrow(
				() -> new ResourceDoesNotExistException());
		if (noteInput.getTagUris() != null) {
			note.setTags(getTags(noteInput.getTagUris()));
		}
		if (noteInput.getTitle() != null) {
			note.setTitle(noteInput.getTitle());
		}
		if (noteInput.getBody() != null) {
			note.setBody(noteInput.getBody());
		}
		this.noteRepository.save(note);
	}

	private List<Tag> getTags(List<URI> tagLocations) {
		return tagLocations
				.stream()
				.map(location -> this.tagRepository.findById(extractTagId(location))
						.<IllegalArgumentException> orElseThrow(
								() -> new IllegalArgumentException("The tag '" + location
										+ "' does not exist")))
				.collect(Collectors.toList());
	}

	private long extractTagId(URI tagLocation) {
		try {
			String idString = TAG_URI_TEMPLATE.match(tagLocation.toASCIIString()).get(
					"id");
			return Long.valueOf(idString);
		}
		catch (RuntimeException ex) {
			throw new IllegalArgumentException("The tag '" + tagLocation + "' is invalid");
		}
	}
}
