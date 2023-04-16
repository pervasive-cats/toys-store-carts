## [1.0.3](https://github.com/pervasive-cats/toys-store-carts/compare/v1.0.2...v1.0.3) (2023-04-16)


### Bug Fixes

* fix executor in Ditto actor, refactor packages ([70d2c48](https://github.com/pervasive-cats/toys-store-carts/commit/70d2c48081e448733ca6b7dcb39394c126f6f1af))

## [1.0.2](https://github.com/pervasive-cats/toys-store-carts/compare/v1.0.1...v1.0.2) (2023-03-10)


### Bug Fixes

* add database connection pool close on system shutdown ([ff060e2](https://github.com/pervasive-cats/toys-store-carts/commit/ff060e23e9fa7b9ac747c35c8aec6f86ddd7fde0))
* add missing field type while serializing domain events ([ae47150](https://github.com/pervasive-cats/toys-store-carts/commit/ae47150732c87a16f530dd97e2711d9b0ce60949))
* remove code instrumentation when generating jar file ([57a2c2c](https://github.com/pervasive-cats/toys-store-carts/commit/57a2c2c7f8f5c85a2bf7955ae5e4e67758980d57))
* share datasource between all repository instances ([f9ed5ef](https://github.com/pervasive-cats/toys-store-carts/commit/f9ed5ef91d1a71395b4b8706c2489fd4a4b18a0f))

## [1.0.1](https://github.com/pervasive-cats/toys-store-carts/compare/v1.0.0...v1.0.1) (2023-03-02)


### Bug Fixes

* change old implementation for new ditto client one ([52217df](https://github.com/pervasive-cats/toys-store-carts/commit/52217df86de4daba04e85676d3e96b641ce46f5d))
* fix cart thing model error types and base URI ([d7d0a20](https://github.com/pervasive-cats/toys-store-carts/commit/d7d0a20e65f51424e01edb4bacde71e7140cccc0))

# 1.0.0 (2023-02-26)


### Bug Fixes

* add missing property and actions to cart thing model, simplify schema, make properties readonly ([01555d2](https://github.com/pervasive-cats/toys-store-carts/commit/01555d2d7d33be1a0ea6e2e032950b5331ebe88e))
* add type classes for AssociatedCart, LockedCart and UnlockedCart ([14760c5](https://github.com/pervasive-cats/toys-store-carts/commit/14760c588219a3a387f547ffb418bbee296dbd69))
* change carts table p_key to (cartId, store) and update add cart ([157ce0f](https://github.com/pervasive-cats/toys-store-carts/commit/157ce0fc4acae23f0682e40e623ecf41ca413c9c))
* change carts table primary key, repository add cart method ([ca492d7](https://github.com/pervasive-cats/toys-store-carts/commit/ca492d735df557def3a38e188612fa94475524e4))
* change return type of add cart repository method ([5ebfa55](https://github.com/pervasive-cats/toys-store-carts/commit/5ebfa55e22e25f0e34b6b06f4e8fd5ba02e3335c))
* corrected onItemInsertedIntoCart metho  parameter ([91bef24](https://github.com/pervasive-cats/toys-store-carts/commit/91bef2433ecc855a3bc65ad7cd2a1def3d2f9ab0))
* edit docker-compose restart rules ([611d00b](https://github.com/pervasive-cats/toys-store-carts/commit/611d00b88dc86a5366dd1f321148d46392b4f9f6))
* fix cart thing model for correcting schemas and content types of affordances ([d471c05](https://github.com/pervasive-cats/toys-store-carts/commit/d471c0508dc933c5120c3a5e84ae23636ea39511))
* fix definition of Thing Model for cart and its path ([da972cc](https://github.com/pervasive-cats/toys-store-carts/commit/da972cca60c50962d9cac8d66dad5a094a8eeb40))
* fix Ditto actor boot sequence ([dd762e4](https://github.com/pervasive-cats/toys-store-carts/commit/dd762e457bb4526d8477f14849f9bbba7dbfb6b9))
* prepare microservice for release ([33b68cd](https://github.com/pervasive-cats/toys-store-carts/commit/33b68cd1e196a548eef828c0f860d1998d32f6e7))
* remove toString override, change hashCode implementation ([76b578a](https://github.com/pervasive-cats/toys-store-carts/commit/76b578a335cdf76d68ada8bbfb0ee9befd5d2929))
* remove updating of cart id in database ([4b77c5f](https://github.com/pervasive-cats/toys-store-carts/commit/4b77c5fcd7830a9c338b7ce76aaa518c17b9d831))


### Features

* add all Ditto commands ([01fc85c](https://github.com/pervasive-cats/toys-store-carts/commit/01fc85cc5929893233303ba81a5a6bdadcafd1b8))
* add cart interface ([af1a726](https://github.com/pervasive-cats/toys-store-carts/commit/af1a726aa6fe545934140ad877c0fbd04e064c2f))
* add cart server actor and routes, add message broker actor ([fb2826d](https://github.com/pervasive-cats/toys-store-carts/commit/fb2826d6de3ca74674d3ee30c123418f81d5d185))
* add cart Thing Model ([1162fc7](https://github.com/pervasive-cats/toys-store-carts/commit/1162fc71a3d1286b8619422e882e17a9eee39f72))
* add CartId ([98e32f8](https://github.com/pervasive-cats/toys-store-carts/commit/98e32f8e7768c36e03018d0238bf2ae6fdf78358))
* add customer interface ([dd7f540](https://github.com/pervasive-cats/toys-store-carts/commit/dd7f5405d7ef824d72ac9f98d35fa3c2a009af0c))
* add customUnregistered interface ([d1dbf4c](https://github.com/pervasive-cats/toys-store-carts/commit/d1dbf4ca1933a919f89291af2ee742ed4633052e))
* add Ditto actor for ditto interaction ([32c1e70](https://github.com/pervasive-cats/toys-store-carts/commit/32c1e70e099c9ecd23ca76d83d10cb119a33c6a3))
* add events interfaces ([f989036](https://github.com/pervasive-cats/toys-store-carts/commit/f989036c22942fd33c41972d23e0d091ecce3839))
* add implementation for all entities ([ea46865](https://github.com/pervasive-cats/toys-store-carts/commit/ea4686581d48ad091cd9b54bf49971d6345ae14d))
* add implementation for all value objects ([b71d075](https://github.com/pervasive-cats/toys-store-carts/commit/b71d075cfb53c8c33e427dc46429adffc68f4eb8))
* add implementation for domain events ([13c8fe5](https://github.com/pervasive-cats/toys-store-carts/commit/13c8fe5842d0bcd31f5f4ef0e4239678e3ebb36e))
* add implementation for findByStore repository method, fix carts case class ([11ac5c7](https://github.com/pervasive-cats/toys-store-carts/commit/11ac5c79299f5e8cbb33b61aa6a03a131d43a306))
* add implementation for repository update method, add tests ([4364565](https://github.com/pervasive-cats/toys-store-carts/commit/4364565052570f98b0caec505f21b8320b899db6))
* add locked, unlocked and associated cart interfaces ([c90f687](https://github.com/pervasive-cats/toys-store-carts/commit/c90f687890d0bdfdfa55e56f95ef5c4562ec5ce2))
* add override for toString, equals and hashCode for carts ([3a2324d](https://github.com/pervasive-cats/toys-store-carts/commit/3a2324d140420842912f68641180d2e82698dca2))
* add repository implementation for findById, add and remove methods, add tests for locked and unlocked carts ([6b241b7](https://github.com/pervasive-cats/toys-store-carts/commit/6b241b72179f5e1e36504d3736b127ac80093482))
* add Repository interface ([740c138](https://github.com/pervasive-cats/toys-store-carts/commit/740c138f97b12c6c00a2e8ab1d70a52f15cd387e))
* add service interfaces ([ef2650b](https://github.com/pervasive-cats/toys-store-carts/commit/ef2650bb38615d9896642391fa0c5a557e0a9130))
* add Store ([5b09ea8](https://github.com/pervasive-cats/toys-store-carts/commit/5b09ea87ad082ac0bd7c0da428b961e355415b87))
* add Store interface ([41776e0](https://github.com/pervasive-cats/toys-store-carts/commit/41776e04aec33d105bfe064faa41eaacafc49219))
* implemented Customer ([5a0d0fc](https://github.com/pervasive-cats/toys-store-carts/commit/5a0d0fcd3d3bd8a993cc532aee35572fe1ae752b))
