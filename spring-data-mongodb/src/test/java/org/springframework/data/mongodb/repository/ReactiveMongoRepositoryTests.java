/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.data.mongodb.repository;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.springframework.data.domain.Sort.Direction.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Publisher;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.Person.Sex;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.data.mongodb.repository.support.SimpleReactiveMongoRepository;
import org.springframework.data.repository.query.DefaultEvaluationContextProvider;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.test.TestSubscriber;

/**
 * Test for {@link ReactiveMongoRepository} query methods.
 *
 * @author Mark Paluch
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:reactive-infrastructure.xml")
public class ReactiveMongoRepositoryTests implements BeanClassLoaderAware, BeanFactoryAware {

	@Autowired ReactiveMongoTemplate template;

	ReactiveMongoRepositoryFactory factory;
	private ClassLoader classLoader;
	private BeanFactory beanFactory;
	private ReactivePersonRepostitory repository;

	Person dave, oliver, carter, boyd, stefan, leroi, alicia;

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader == null ? org.springframework.util.ClassUtils.getDefaultClassLoader() : classLoader;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Before
	public void setUp() throws Exception {

		factory = new ReactiveMongoRepositoryFactory(template);
		factory.setRepositoryBaseClass(SimpleReactiveMongoRepository.class);
		factory.setBeanClassLoader(classLoader);
		factory.setBeanFactory(beanFactory);
		factory.setEvaluationContextProvider(DefaultEvaluationContextProvider.INSTANCE);

		repository = factory.getRepository(ReactivePersonRepostitory.class);

		repository.deleteAll().block();

		dave = new Person("Dave", "Matthews", 42);
		oliver = new Person("Oliver August", "Matthews", 4);
		carter = new Person("Carter", "Beauford", 49);
		carter.setSkills(Arrays.asList("Drums", "percussion", "vocals"));
		Thread.sleep(10);
		boyd = new Person("Boyd", "Tinsley", 45);
		boyd.setSkills(Arrays.asList("Violin", "Electric Violin", "Viola", "Mandolin", "Vocals", "Guitar"));
		stefan = new Person("Stefan", "Lessard", 34);
		leroi = new Person("Leroi", "Moore", 41);

		alicia = new Person("Alicia", "Keys", 30, Sex.FEMALE);

		TestSubscriber<Person> subscriber = TestSubscriber.create();
		repository.save(Arrays.asList(oliver, dave, carter, boyd, stefan, leroi, alicia)).subscribe(subscriber);

		subscriber.await().assertComplete().assertNoError();
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindByLastName() throws Exception {

		List<Person> list = repository.findByLastname("Matthews").collectList().block();

		assertThat(list, hasSize(2));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindMonoOfPage() throws Exception {

		Mono<Page<Person>> pageMono = repository.findMonoPageByLastname("Matthews", new PageRequest(0, 1));

		Page<Person> persons = pageMono.block();

		assertThat(persons.getContent(), hasSize(1));
		assertThat(persons.getTotalPages(), is(2));

		pageMono = repository.findMonoPageByLastname("Matthews", new PageRequest(0, 100));

		persons = pageMono.block();

		assertThat(persons.getContent(), hasSize(2));
		assertThat(persons.getTotalPages(), is(1));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindMonoOfSlice() throws Exception {

		Mono<Slice<Person>> pageMono = repository.findMonoSliceByLastname("Matthews", new PageRequest(0, 1));

		Slice<Person> persons = pageMono.block();

		assertThat(persons.getContent(), hasSize(1));
		assertThat(persons.hasNext(), is(true));

		pageMono = repository.findMonoSliceByLastname("Matthews", new PageRequest(0, 100));

		persons = pageMono.block();

		assertThat(persons.getContent(), hasSize(2));
		assertThat(persons.hasNext(), is(false));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindOneByLastName() throws Exception {

		Person carter = repository.findOneByLastname("Beauford").block();

		assertThat(carter.getFirstname(), is(equalTo("Carter")));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindOneByPublisherOfLastName() throws Exception {

		Person carter = repository.findByLastname(Mono.just("Beauford")).block();

		assertThat(carter.getFirstname(), is(equalTo("Carter")));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindByPublisherOfLastNameIn() throws Exception {

		List<Person> persons = repository.findByLastnameIn(Flux.just("Beauford", "Matthews")).collectList().block();

		assertThat(persons, hasItems(carter, dave, oliver));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindByPublisherOfLastNameInAndAgeGreater() throws Exception {

		List<Person> persons = repository.findByLastnameInAndAgeGreaterThan(Flux.just("Beauford", "Matthews"), 41)
				.collectList().block();

		assertThat(persons, hasItems(carter, dave));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindUsingPublishersInStringQuery() throws Exception {

		List<Person> persons = repository.findStringQuery(Flux.just("Beauford", "Matthews"), Mono.just(41)).collectList()
				.block();

		assertThat(persons, hasItems(carter, dave));
	}

	/**
	 * @see DATAMONGO-1444
	 */
	@Test
	public void shouldFindByLastNameAndSort() throws Exception {

		List<Person> persons = repository.findByLastname("Matthews", new Sort(new Order(ASC, "age"))).collectList().block();
		assertThat(persons, contains(oliver, dave));

		persons = repository.findByLastname("Matthews", new Sort(new Order(DESC, "age"))).collectList().block();
		assertThat(persons, contains(dave, oliver));
	}

	static interface ReactivePersonRepostitory extends ReactiveMongoRepository<Person, String> {

		/**
		 * Returns all {@link Person}s with the given lastname.
		 *
		 * @param lastname
		 * @return
		 */
		Flux<Person> findByLastname(String lastname);

		Mono<Person> findOneByLastname(String lastname);

		Mono<Page<Person>> findMonoPageByLastname(String lastname, Pageable pageRequest);

		Mono<Slice<Person>> findMonoSliceByLastname(String lastname, Pageable pageRequest);

		Mono<Person> findByLastname(Publisher<String> lastname);

		Flux<Person> findByLastnameIn(Publisher<String> lastname);

		Flux<Person> findByLastname(String lastname, Sort sort);

		Flux<Person> findByLastnameInAndAgeGreaterThan(Flux<String> lastname, int age);

		@Query("{ lastname: { $in: ?0 }, age: { $gt : ?1 } }")
		Flux<Person> findStringQuery(Flux<String> lastname, Mono<Integer> age);
	}
}