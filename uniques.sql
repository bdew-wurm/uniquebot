CREATE TABLE `uniques` (
  `id` bigint(20) NOT NULL,
  `server` varchar(50) NOT NULL,
  `name` varchar(255) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `reporter` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;